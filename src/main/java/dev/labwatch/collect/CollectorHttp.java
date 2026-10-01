package dev.labwatch.collect;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Enforces the request deadline through receipt of the entire response body. */
final class CollectorHttp {
    private CollectorHttp() {}

    static HttpResponse<String> send(HttpClient client, HttpRequest request) throws IOException {
        var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            return response.get(request.timeout().orElseThrow().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            response.cancel(true);
            throw new HttpTimeoutException("request timed out");
        } catch (InterruptedException e) {
            response.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("collector request interrupted", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw new IOException("collector request failed", e.getCause());
        }
    }
}
