package org.example;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import lombok.Getter;
import lombok.Setter;
import okhttp3.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private long timeDuration;
    private int requestLimit;
    private final String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private long lastRequestTime = System.currentTimeMillis();
    private int currentRequests = 0;

    public String createDocument(Document document, String signature) throws IOException, InterruptedException {
        checkTime();

        RequestBody requestBody = RequestBody.create(
                OBJECT_MAPPER.writeValueAsString(document), JSON);
        Request request = new Request.Builder()
                .url(apiUrl)
                .post(requestBody)
                .addHeader("Signature", signature)
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public synchronized void checkTime() throws InterruptedException {
        long timeNow = System.currentTimeMillis();
        long timeDifference = timeNow - lastRequestTime;
        if (timeDifference >= timeDuration) {
            lastRequestTime = timeNow;
            currentRequests = 0;
        }
        if (currentRequests >= requestLimit) {
            long waitTime = lastRequestTime + timeDuration - timeNow;
            if (waitTime > 0) {
                Thread.sleep(waitTime);
            }
            currentRequests = 0;
            lastRequestTime = System.currentTimeMillis();
        }
        currentRequests++;
    }

    public CrptApi(TimeUnit timeUnit, int requestLimit, int duration) {
        this.timeDuration = timeUnit.toMillis(duration);
        if (requestLimit > 0) {
            this.requestLimit = requestLimit;
        } else {throw new RuntimeException("Максимальное количество запросов должно быть более 0");}
    }

    @Getter
    @Setter
    public static class Document {

        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type = "LP_INTRODUCE_GOODS";
        private Boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-d")
        @JsonDeserialize(using = LocalDateDeserializer.class)
        @JsonSerialize(using = LocalDateSerializer.class)
        private LocalDate production_date;
        private String production_type;
        private String[] products;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-d")
        @JsonDeserialize(using = LocalDateDeserializer.class)
        @JsonSerialize(using = LocalDateSerializer.class)
        private LocalDate reg_date;
        private String reg_number;

        public Document(String id) {
            this.doc_id = id;
        }

        @Override
        public String toString() {
            return "{" +
                    "description='" + description + '\'' +
                    ", doc_id='" + doc_id + '\'' +
                    ", doc_status='" + doc_status + '\'' +
                    ", doc_type='" + doc_type + '\'' +
                    ", importRequest=" + importRequest +
                    ", owner_inn='" + owner_inn + '\'' +
                    ", participant_inn='" + participant_inn + '\'' +
                    ", producer_inn='" + producer_inn + '\'' +
                    ", production_date=" + production_date +
                    ", production_type='" + production_type + '\'' +
                    ", products=" + Arrays.toString(products) +
                    ", reg_date=" + reg_date +
                    ", reg_number='" + reg_number + '\'' +
                    '}';
        }
    }

    @Getter
    @Setter
    public static class Description {
        private String participantInn;

        @Override
        public String toString() {
            return "{" +
                    "participantInn='" + participantInn + '\'' +
                    '}';
        }
    }

    @Getter
    @Setter
    public static class Products {
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

    public static void main(String[] args) throws IOException, InterruptedException {
        CrptApi crptApi = new CrptApi(TimeUnit.MILLISECONDS, 2, 200);
        Document document = fillDocument();

        int numThreads = 100;
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                try {
                    crptApi.createDocument(document, "sampleSignature");
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            });
            threads[i].start();
        }
        // Ждем завершения всех потоков
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static Document fillDocument() throws JsonProcessingException {
        LocalDate localDate = LocalDate.of(2000, 1, 2);
        LocalDate localDateNow = LocalDate.now();

        ObjectMapper objectMapper = new ObjectMapper();
        Products products = new Products();
        products.setCertificate_document("String");
        products.setCertificate_document_date(localDateNow);
        products.setCertificate_document_number("String");
        products.setOwner_inn("String");
        products.setProducer_inn("String");
        products.setProduction_date(localDate);
        products.setTnved_code("String");
        products.setUit_code("String");
        products.setUitu_code("String");
        String productToString = objectMapper.writeValueAsString(products);
        String[] splitProduct = productToString.split(",");

        Description description = new Description();
        description.setParticipantInn("String");

        Document document = new Document("String");
        document.setDescription(description);
        document.setDoc_status("String");
        document.setDoc_type("LP_INTRODUCE_GOODS");
        document.setImportRequest(true);
        document.setOwner_inn("String");
        document.setParticipant_inn("String");
        document.setProducer_inn("String");
        document.setProduction_date(localDateNow);
        document.setProduction_type("String");
        document.setProducts(splitProduct);
        document.setReg_date(localDate);
        document.setReg_number("String");
        return document;
    }


}
