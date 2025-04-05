package org.example;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private final int requestPerSecond = 1;
    private final long timeDuration;
    private final int requestLimit;

    private final String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Getter
    private final Queue<DataToSend> queue = new ConcurrentLinkedQueue<>();
    private final Deque<Long> timestampDeque = new LinkedList<>();
    private final ScheduledExecutorService schedulerSender = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService schedulerLogger = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger sentInCurrentSecond = new AtomicInteger(0);


    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeDuration = timeUnit.toMillis(requestPerSecond);
        if (requestLimit > 0) {
            this.requestLimit = requestLimit;
        } else {
            throw new RuntimeException("Максимальное количество запросов должно быть более 0");
        }
        startProcessing();
        startStatsLogger();
    }

    private void startProcessing() {
        schedulerSender.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            // Reset old timestamps
            while (!timestampDeque.isEmpty() && now - timestampDeque.peekFirst() >= timeDuration) {
                timestampDeque.pollFirst();
            }
            while (timestampDeque.size() < requestLimit && !queue.isEmpty()) {
                timestampDeque.addLast(System.currentTimeMillis());
                DataToSend data = queue.poll();
                if (data == null) break;
                sendToApi(data);
            }
        }, 0, 50, TimeUnit.MILLISECONDS); // check every 50ms
    }

    private void startStatsLogger() {
        schedulerLogger.scheduleAtFixedRate(() -> {
            int count = sentInCurrentSecond.getAndSet(0);
            System.out.printf("[Stats] Sent %d requests in last second%n", count);
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void sendToApi(DataToSend dataToSend) {
        sentInCurrentSecond.incrementAndGet(); //Logger counter per second
        try {
            String jsonBody = OBJECT_MAPPER.writeValueAsString(dataToSend.document);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Signature", dataToSend.signature)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.printf("Doc id %s Response: %d%n", dataToSend.document.doc_id, response.statusCode());
        } catch (Exception e) {
            System.err.println("Error sending request: " + e.getMessage());
        }
    }

    public void enqueueRequest(DataToSend data) {
        queue.add(data);
    }

    public void shutdown() {
        schedulerSender.shutdown();
        schedulerLogger.shutdown();
        System.out.println("Send stopped");
    }


    public record DataToSend(Document document, String signature) {
    }

    @Getter
    @Setter
    public static class Document {
        private String doc_id;
        private Description description;
        private String doc_status;
        private String doc_type = "LP_INTRODUCE_GOODS";
        private Boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private ProductionType production_type;
        private List<Product> products;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-d")
        @JsonDeserialize(using = LocalDateDeserializer.class)
        @JsonSerialize(using = LocalDateSerializer.class)
        private LocalDate reg_date;
        private String reg_number;
    }

    public enum ProductionType {
        OWN_PRODUCTION,
        CONTRACT_PRODUCTION
    }

    @Getter
    @Setter
    public static class Description {
        private String participantInn;
    }

    @Getter
    @Setter
    public static class Product {
        private String productId;
        private String certificate_document;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-d")
        @JsonDeserialize(using = LocalDateDeserializer.class)
        @JsonSerialize(using = LocalDateSerializer.class)
        private LocalDate certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-d")
        @JsonDeserialize(using = LocalDateDeserializer.class)
        @JsonSerialize(using = LocalDateSerializer.class)
        private LocalDate production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }

    public static DataToSend fillDocument() {
        LocalDate localDate = LocalDate.of(2000, 1, 2);

        Product products = new Product();
        products.setCertificate_document("String");
        products.setCertificate_document_date(LocalDate.now());
        products.setCertificate_document_number("String");
        products.setOwner_inn("String");
        products.setProducer_inn("String");
        products.setProduction_date(localDate);
        products.setTnved_code("String");
        products.setUit_code("String");
        products.setUitu_code("String");

        Description description = new Description();
        description.setParticipantInn("String");

        Document document = new Document();
        document.setDescription(description);
        document.setDoc_status("String");
        document.setDoc_type("LP_INTRODUCE_GOODS");
        document.setImportRequest(true);
        document.setOwner_inn("String");
        document.setParticipant_inn("String");
        document.setProducer_inn("String");
        document.setProduction_date(localDate.toString());
        document.setProduction_type(ProductionType.CONTRACT_PRODUCTION);
        document.setProducts(List.of(products));
        document.setReg_date(localDate);
        document.setReg_number("String");
        return new DataToSend(document, "Signature");
    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 2);

        // init queue request
        new Thread(() -> {
            int countRequest = 20;
            for (int j = 0; j < countRequest; j++) {
                DataToSend dataToSend;
                dataToSend = fillDocument();
                dataToSend.document.setDoc_id(String.valueOf(j));
                crptApi.enqueueRequest(dataToSend);
            }
            System.out.println("\nTotal requests received: " + countRequest);
        }).start();

        // do request while queue is not empty
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            while (true) {
                if (crptApi.getQueue().isEmpty()) {
                    crptApi.shutdown();
                    return;
                }
            }
        }).start();
    }
}
