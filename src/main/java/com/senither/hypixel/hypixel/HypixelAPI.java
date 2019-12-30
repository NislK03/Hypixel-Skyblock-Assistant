/*
 * Copyright (c) 2019.
 *
 * This file is part of Hypixel Skyblock Assistant.
 *
 * Hypixel Guild Synchronizer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Hypixel Guild Synchronizer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Hypixel Guild Synchronizer.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.senither.hypixel.hypixel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.senither.hypixel.Configuration;
import com.senither.hypixel.SkyblockAssistant;
import com.senither.hypixel.contracts.hypixel.Response;
import com.senither.hypixel.exceptions.HypixelAPIException;
import com.senither.hypixel.exceptions.InvalidRequestURIException;
import com.senither.hypixel.exceptions.RatelimiteReachedException;
import com.senither.hypixel.hypixel.adapter.DateTimeTypeAdapter;
import com.senither.hypixel.hypixel.adapter.UUIDTypeAdapter;
import com.senither.hypixel.hypixel.responses.PlayerResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HypixelAPI {

    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
        .registerTypeAdapter(ZonedDateTime.class, new DateTimeTypeAdapter())
        .create();

    private static final String endpoint = "https://api.hypixel.net/";

    private final Configuration configuration;
    private final ExecutorService executorService;
    private final HttpClient httpClient;

    public HypixelAPI(SkyblockAssistant app) {
        this.configuration = app.getConfiguration();

        this.executorService = Executors.newCachedThreadPool();
        this.httpClient = HttpClientBuilder.create().build();
    }

    public CompletableFuture<PlayerResponse> getPlayer(String username) {
        return sendRequest(PlayerResponse.class, createRequestURI("player", new HashMap<String, String>() {{
            put("name", username);
        }}));
    }

    private <R extends Response> CompletableFuture<R> sendRequest(Class<R> clazz, String uri) {
        CompletableFuture<R> future = new CompletableFuture<>();

        executorService.submit(() -> {
            if (uri == null) {
                future.completeExceptionally(new InvalidRequestURIException("Invalid request URI given, the URI is null!"));
                return;
            }

            try {
                R response = httpClient.execute(new HttpGet(uri), obj -> {
                    String content = EntityUtils.toString(obj.getEntity(), "UTF-8");
                    return GSON.fromJson(content, clazz);
                });

                if (!response.isSuccess()) {
                    throw new HypixelAPIException();
                }

                if (response.isThrottle()) {
                    throw new RatelimiteReachedException();
                }

                future.complete(response);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private String createRequestURI(String route, HashMap<String, String> values) {
        StringBuilder uri = new StringBuilder(endpoint + route);

        uri.append("?key=").append(configuration.getHypixelToken());

        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                try {
                    uri.append("&")
                        .append(entry.getKey())
                        .append("=")
                        .append(URLEncoder.encode(
                            entry.getValue(),
                            StandardCharsets.UTF_8.toString()
                        ));
                } catch (UnsupportedEncodingException e) {
                    return null;
                }
            }
        }

        return uri.toString();
    }
}