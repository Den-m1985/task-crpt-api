package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private TimeUnit timeUnit;
    private int requestLimit;
    private final String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    private final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ReentrantLock lock = new ReentrantLock();
    private long lastRequestTime = Instant.EPOCH.toEpochMilli();
    private int currentRequests = 0;


    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
    }

    public void createDocument(Document document, String signature) throws IOException, InterruptedException {
        lock.lock();
        try {
            long currentTime = Instant.now().toEpochMilli();
            long timeDifference = currentTime - lastRequestTime;
            if (timeDifference >= TimeUnit.MILLISECONDS.convert(1, timeUnit)) {
                lastRequestTime = currentTime;
                currentRequests = 0;
            }
            while (currentRequests >= requestLimit) {
                long waitTime = TimeUnit.MILLISECONDS.convert(1, timeUnit) - timeDifference;
                lock.unlock();
                Thread.sleep(waitTime);
                lock.lock();
                timeDifference = Instant.now().toEpochMilli() - lastRequestTime;
            }
            currentRequests++;

            RequestBody requestBody = RequestBody.create(
                    MediaType.get("application/json; charset=utf-8"),
                    OBJECT_MAPPER.writeValueAsString(document)
            );
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .post(requestBody)
                    .addHeader("Signature", signature)
                    .build();
            HTTP_CLIENT.newCall(request).execute();
        } finally {
            lock.unlock();
        }
    }

    @Getter
    @Setter
    class Document {
        private String description;
        private String doc_id;
        private String doc_status;
        private String doc_type = "LP_INTRODUCE_GOODS";
        private Boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private Date production_date;
        private String production_type;
        private String[] products;
        private Date reg_date;
        private String reg_number;
    }

    @Getter
    @Setter
    class Products {
        private String certificateDocument;
        private Date certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private Date productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;
    }

}
