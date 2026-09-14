package fr.bonamy.sports.mobile;

import android.util.Base64;
import com.getcapacitor.*;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;

@CapacitorPlugin(name = "SportsHttp")
public class SportsHttpPlugin extends Plugin {
    private final ConcurrentHashMap<String, Call> pending = new ConcurrentHashMap<>();
    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .addInterceptor(okhttp3.brotli.BrotliInterceptor.INSTANCE)
        .dns(host -> {
            List<InetAddress> addresses = Dns.SYSTEM.lookup(host);
            for (InetAddress address : addresses) {
                byte[] b = address.getAddress();
                int a = b[0] & 255, second = b[1] & 255;
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() ||
                    address.isSiteLocalAddress() || address.isMulticastAddress() ||
                    (b.length == 4 && (a == 0 || a >= 224 || (a == 100 && second >= 64 && second <= 127) ||
                     (a == 198 && second >= 18 && second <= 19) || (a == 192 && second == 0))) ||
                    (b.length == 16 && (a & 224) != 32)) throw new UnknownHostException("Destination is not public");
            }
            return addresses;
        })
        .addNetworkInterceptor(chain -> {
            java.util.concurrent.atomic.AtomicInteger attempts = chain.request().tag(java.util.concurrent.atomic.AtomicInteger.class);
            if (attempts == null || attempts.incrementAndGet() > 6) throw new IOException("Too many redirects");
            HttpUrl url = chain.request().url();
            if (!url.isHttps() || !url.username().isEmpty() || !url.password().isEmpty())
                throw new IOException("Invalid destination");
            // OkHttp resolves each redirect through the same validating DNS implementation.
            return chain.proceed(chain.request());
        }).build();

    @PluginMethod
    public void request(PluginCall call) {
        String id = call.getString("id"), url = call.getString("url");
        Integer limit = call.getInt("limit");
        JSObject headers = call.getObject("headers");
        if (id == null || id.length() > 100 || url == null || url.length() > 16384 || limit == null ||
            limit < 1 || limit > 33554432 || headers == null || pending.size() >= 64) {
            call.reject("Invalid request"); return;
        }
        try {
            Request.Builder builder = new Request.Builder().url(url).tag(java.util.concurrent.atomic.AtomicInteger.class, new java.util.concurrent.atomic.AtomicInteger());
            if (!builder.build().url().isHttps()) throw new IOException();
            Iterator<String> names = headers.keys();
            while (names.hasNext()) { String name = names.next(); builder.header(name, headers.getString(name)); }
            Call request = client.newCall(builder.build());
            if (pending.putIfAbsent(id, request) != null) { call.reject("Invalid request"); return; }
            request.enqueue(new Callback() {
                public void onFailure(Call request, IOException error) {
                    pending.remove(id, request); call.reject("Source unavailable");
                }
                public void onResponse(Call request, Response response) {
                    try (response) {
                        int redirects = 0;
                        for (Response prior = response.priorResponse(); prior != null; prior = prior.priorResponse()) redirects++;
                        if (redirects > 5 || !response.isSuccessful() || response.body() == null || response.body().contentLength() > limit)
                            throw new IOException();
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        InputStream input = response.body().byteStream();
                        // Transparent gzip is handled by OkHttp. Decode other advertised encodings explicitly.
                        String encoding = response.header("Content-Encoding", "identity");
                        if (encoding.equalsIgnoreCase("gzip")) input = new java.util.zip.GZIPInputStream(input);
                        else if (encoding.equalsIgnoreCase("deflate")) input = new java.util.zip.InflaterInputStream(input);
                        else if (!encoding.equalsIgnoreCase("identity")) throw new IOException();
                        byte[] buffer = new byte[16384]; int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (request.isCanceled() || output.size() + count > limit) throw new IOException();
                            output.write(buffer, 0, count);
                        }
                        JSObject result = new JSObject();
                        result.put("url", response.request().url().toString());
                        result.put("status", response.code());
                        result.put("data", Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP));
                        call.resolve(result);
                    } catch (Exception error) { call.reject("Source unavailable"); }
                    finally { pending.remove(id, request); }
                }
            });
        } catch (Exception error) { call.reject("Invalid request"); }
    }
    @PluginMethod
    public void cancel(PluginCall call) {
        Call request = pending.get(call.getString("id", ""));
        if (request != null) request.cancel();
        call.resolve();
    }
    @Override protected void handleOnDestroy() {
        for (Call request : pending.values()) request.cancel();
        pending.clear();
    }
}
