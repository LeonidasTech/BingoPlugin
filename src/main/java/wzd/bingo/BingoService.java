package wzd.bingo;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.ImageUtil;
import okhttp3.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

@Slf4j
@Singleton
public class BingoService
{
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final int HEARTBEAT_INTERVAL_SECONDS = 60;
    
    @Inject
    private BingoConfig config;
    
    @Inject
    private ConfigManager configManager;
    
    @Inject
    private OkHttpClient httpClient;
    
    private final Gson gson = new Gson();
    private ScheduledExecutorService heartbeatExecutor;
    private volatile boolean isAuthenticated = false;

    /**
     * Authenticate user with Discord ID and RSN
     * @param rsn The RuneScape username (auto-detected from client)
     * @param discordId The Discord user ID for authentication
     * @return Optional containing success message if authentication successful
     */
    public Optional<String> authenticateWithDiscord(String rsn, String discordId)
    {
        log.info("Attempting authentication for RSN: {} with Discord ID: {}", rsn, discordId);
        
        String apiEndpoint = config.authApiUrl() + "/api/auth/login";
        
        // Create request body
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("rsn", rsn);
        requestBody.addProperty("discordId", discordId);
        
        RequestBody body = RequestBody.create(
            MediaType.get(CONTENT_TYPE_JSON),
            gson.toJson(requestBody)
        );
        
        Request request = new Request.Builder()
            .url(apiEndpoint)
            .post(body)
            .addHeader("Content-Type", CONTENT_TYPE_JSON)
            .build();
        
        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful() && response.body() != null)
            {
                String responseJson = response.body().string();
                JsonObject responseObj = gson.fromJson(responseJson, JsonObject.class);
                
                // Extract authentication data
                String token = responseObj.get("token").getAsString();
                JsonObject user = responseObj.getAsJsonObject("user");
                String teamId = user.get("teamId").getAsString();
                
                // Store authentication data
                configManager.setConfiguration("bingo", "jwtToken", token);
                configManager.setConfiguration("bingo", "rsn", rsn);
                configManager.setConfiguration("bingo", "discordId", discordId);
                configManager.setConfiguration("bingo", "teamId", teamId);
                configManager.setConfiguration("bingo", "isAuthenticated", true);
                
                isAuthenticated = true;
                log.info("Authentication successful for RSN: {} (Team: {})", rsn, teamId);
                return Optional.of("Authentication successful");
            }
            else
            {
                log.warn("Authentication failed: HTTP {}", response.code());
                if (response.body() != null)
                {
                    log.warn("Error response: {}", response.body().string());
                }
            }
        }
        catch (IOException e)
        {
            log.error("Authentication request failed", e);
        }
        
        return Optional.empty();
    }

    /**
     * Legacy method for backward compatibility
     */
    public Optional<String> authenticateWithToken(String rsn, String token)
    {
        // For backward compatibility, treat the "token" as discordId
        return authenticateWithDiscord(rsn, token);
    }

    /**
     * Fetch user's bingo board data
     * @param rsn The RuneScape username
     * @return Optional containing board data if successful
     */
    public Optional<JsonObject> fetchBoardData(String rsn)
    {
        String apiEndpoint = config.authApiUrl() + "/api/bingo/board/" + rsn;
        
        Request request = new Request.Builder()
            .url(apiEndpoint)
            .get()
            .addHeader("Authorization", "Bearer " + config.jwtToken())
            .build();
        
        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful() && response.body() != null)
            {
                String responseJson = response.body().string();
                JsonObject boardData = gson.fromJson(responseJson, JsonObject.class);
                log.info("Successfully fetched board data for RSN: {}", rsn);
                return Optional.of(boardData);
            }
            else if (response.code() == 401)
            {
                log.warn("JWT token expired, authentication required");
                isAuthenticated = false;
                return Optional.empty();
            }
            else
            {
                log.warn("Failed to fetch board data: HTTP {}", response.code());
            }
        }
        catch (IOException e)
        {
            log.error("Board data request failed", e);
        }
        
        return Optional.empty();
    }

    /**
     * Fetch team progress and statistics
     * @param teamId The team ID
     * @return Optional containing team data if successful
     */
    public Optional<JsonObject> fetchTeamData(String teamId)
    {
        if (teamId == null || teamId.isEmpty())
        {
            log.warn("Cannot fetch team data - no team ID provided");
            return Optional.empty();
        }
        
        String apiEndpoint = config.authApiUrl() + "/api/bingo/team/" + teamId;
        
        Request request = new Request.Builder()
            .url(apiEndpoint)
            .get()
            .addHeader("Authorization", "Bearer " + config.jwtToken())
            .build();
        
        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful() && response.body() != null)
            {
                String responseJson = response.body().string();
                JsonObject teamData = gson.fromJson(responseJson, JsonObject.class);
                log.info("Successfully fetched team data for team: {}", teamId);
                return Optional.of(teamData);
            }
            else if (response.code() == 401)
            {
                log.warn("JWT token expired, authentication required");
                isAuthenticated = false;
                return Optional.empty();
            }
            else
            {
                log.warn("Failed to fetch team data: HTTP {}", response.code());
            }
        }
        catch (IOException e)
        {
            log.error("Team data request failed", e);
        }
        
        return Optional.empty();
    }

    /**
     * Submit tile completion with evidence
     * @param tileId The ID of the completed tile
     * @param method The completion method (drop, action, etc.)
     * @param evidenceText Text description of the evidence
     * @param screenshot Screenshot as BufferedImage
     * @return true if submission was successful
     */
    public boolean submitTileCompletion(String tileId, String method, String evidenceText, BufferedImage screenshot)
    {
        String apiEndpoint = config.authApiUrl() + "/api/bingo/submit";
        String rsn = config.rsn();
        
        try
        {
            // Convert screenshot to base64
            String screenshotBase64 = convertImageToBase64(screenshot);
            
            // Create request body
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("rsn", rsn);
            requestBody.addProperty("tileId", tileId);
            requestBody.addProperty("method", method);
            requestBody.addProperty("evidenceText", evidenceText);
            requestBody.addProperty("timestamp", System.currentTimeMillis() / 1000);
            requestBody.addProperty("screenshot", screenshotBase64);
            
            RequestBody body = RequestBody.create(
                MediaType.get(CONTENT_TYPE_JSON),
                gson.toJson(requestBody)
            );
            
            Request request = new Request.Builder()
                .url(apiEndpoint)
                .post(body)
                .addHeader("Authorization", "Bearer " + config.jwtToken())
                .addHeader("Content-Type", CONTENT_TYPE_JSON)
                .build();
            
            try (Response response = httpClient.newCall(request).execute())
            {
                if (response.isSuccessful() && response.body() != null)
                {
                    String responseJson = response.body().string();
                    JsonObject responseObj = gson.fromJson(responseJson, JsonObject.class);
                    String status = responseObj.get("status").getAsString();
                    
                    if ("ok".equals(status))
                    {
                        log.info("Successfully submitted tile completion: {}", tileId);
                        return true;
                    }
                }
                else if (response.code() == 401)
                {
                    log.warn("JWT token expired during tile submission");
                    isAuthenticated = false;
                }
                else
                {
                    log.warn("Failed to submit tile completion: HTTP {}", response.code());
                }
            }
        }
        catch (Exception e)
        {
            log.error("Tile submission failed", e);
        }
        
        return false;
    }

    /**
     * Send heartbeat to maintain connection
     */
    public void sendHeartbeat()
    {
        String apiEndpoint = config.authApiUrl() + "/api/bingo/heartbeat";
        String rsn = config.rsn();
        
        if (rsn == null || rsn.isEmpty() || config.jwtToken().isEmpty())
        {
            return; // Not authenticated
        }
        
        // Create request body
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("rsn", rsn);
        requestBody.addProperty("timestamp", System.currentTimeMillis() / 1000);
        
        RequestBody body = RequestBody.create(
            MediaType.get(CONTENT_TYPE_JSON),
            gson.toJson(requestBody)
        );
        
        Request request = new Request.Builder()
            .url(apiEndpoint)
            .post(body)
            .addHeader("Authorization", "Bearer " + config.jwtToken())
            .addHeader("Content-Type", CONTENT_TYPE_JSON)
            .build();
        
        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                log.debug("Heartbeat sent successfully for RSN: {}", rsn);
            }
            else if (response.code() == 401)
            {
                log.warn("JWT token expired during heartbeat");
                isAuthenticated = false;
                stopHeartbeat();
            }
            else
            {
                log.warn("Heartbeat failed: HTTP {}", response.code());
            }
        }
        catch (IOException e)
        {
            log.error("Heartbeat request failed", e);
        }
    }

    /**
     * Start sending periodic heartbeats
     */
    public void startHeartbeat()
    {
        if (heartbeatExecutor == null || heartbeatExecutor.isShutdown())
        {
            heartbeatExecutor = new ScheduledThreadPoolExecutor(1);
            heartbeatExecutor.scheduleAtFixedRate(
                this::sendHeartbeat,
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
            );
            log.info("Started heartbeat service ({}s interval)", HEARTBEAT_INTERVAL_SECONDS);
        }
    }

    /**
     * Stop heartbeat service
     */
    public void stopHeartbeat()
    {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown())
        {
            heartbeatExecutor.shutdown();
            log.info("Stopped heartbeat service");
        }
    }

    /**
     * Initialize the service after authentication
     */
    public void initialize()
    {
        String token = config.jwtToken();
        String rsn = config.rsn();
        
        if (token != null && !token.isEmpty() && rsn != null && !rsn.isEmpty())
        {
            log.info("Initializing Bingo service for user: {}", rsn);
            isAuthenticated = true;
            
            // Start background services
            startHeartbeat();
            
            // Fetch initial data on background thread
            new Thread(() -> {
                fetchBoardData(rsn);
                String teamId = config.teamId();
                if (teamId != null && !teamId.isEmpty())
                {
                    fetchTeamData(teamId);
                }
            }).start();
        }
        else
        {
            log.warn("Cannot initialize Bingo service - missing JWT token or RSN");
        }
    }

    /**
     * Shutdown the service
     */
    public void shutdown()
    {
        stopHeartbeat();
        isAuthenticated = false;
        log.info("Bingo service shutdown complete");
    }

    /**
     * Check if user is currently authenticated
     */
    public boolean isAuthenticated()
    {
        return isAuthenticated && config.jwtToken() != null && !config.jwtToken().isEmpty();
    }

    /**
     * Convert BufferedImage to base64 string
     */
    private String convertImageToBase64(BufferedImage image)
    {
        try
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        }
        catch (IOException e)
        {
            log.error("Failed to convert image to base64", e);
            return "";
        }
    }

    /**
     * Get the configured authentication API base URL
     */
    public String getAuthApiUrl()
    {
        return config.authApiUrl();
    }

    /**
     * Legacy login method - deprecated
     */
    @Deprecated
    public Optional<String> login(String rsn)
    {
        log.warn("Deprecated login method called - use authenticateWithDiscord instead");
        return Optional.empty();
    }

    /**
     * Fetch active bingo events for the authenticated user
     * @return Optional containing active events data if successful
     */
    public Optional<JsonObject> fetchActiveEvents()
    {
        String apiEndpoint = config.authApiUrl() + "/api/bingo/events/active";
        
        Request request = new Request.Builder()
            .url(apiEndpoint)
            .get()
            .addHeader("Authorization", "Bearer " + config.jwtToken())
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .build();
        
        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful() && response.body() != null)
            {
                String responseBody = response.body().string();
                log.info("Active events API response body: {}", responseBody);
                
                // Check if response is empty
                if (responseBody == null || responseBody.trim().isEmpty())
                {
                    log.warn("Empty response from active events API");
                    return Optional.empty();
                }
                
                // Check if response is HTML (common when API returns error pages)
                String trimmedResponse = responseBody.trim();
                if (trimmedResponse.startsWith("<!") || trimmedResponse.startsWith("<html"))
                {
                    log.error("API returned HTML instead of JSON. This usually indicates the API endpoint is not available or there's a server configuration issue.");
                    log.error("Response preview: {}", trimmedResponse.length() > 200 ? trimmedResponse.substring(0, 200) + "..." : trimmedResponse);
                    return Optional.empty();
                }
                
                // Try to parse as JSON
                try
                {
                    JsonElement element = gson.fromJson(responseBody, JsonElement.class);
                    
                    if (element == null)
                    {
                        log.warn("JSON parsing returned null for response: {}", responseBody);
                        return Optional.empty();
                    }
                    
                    if (element.isJsonObject())
                    {
                        JsonObject eventsData = element.getAsJsonObject();
                        log.info("Successfully fetched active events data as object");
                        return Optional.of(eventsData);
                    }
                    else if (element.isJsonArray())
                    {
                        // If array is returned directly, wrap it in an object
                        log.info("API returned array directly, wrapping in object");
                        JsonObject wrappedResponse = new JsonObject();
                        wrappedResponse.addProperty("hasActiveEvent", element.getAsJsonArray().size() > 0);
                        wrappedResponse.add("events", element.getAsJsonArray());
                        return Optional.of(wrappedResponse);
                    }
                    else if (element.isJsonPrimitive())
                    {
                        // API returned a simple string/primitive - create mock response
                        log.warn("API returned primitive response: {}", element.getAsString());
                        JsonObject mockResponse = new JsonObject();
                        mockResponse.addProperty("hasActiveEvent", false);
                        mockResponse.add("activeEvents", new JsonArray());
                        return Optional.of(mockResponse);
                    }
                    else
                    {
                        log.warn("Unexpected JSON response format from active events API");
                        return Optional.empty();
                    }
                }
                catch (Exception jsonException)
                {
                    log.error("Failed to parse JSON response from active events API", jsonException);
                    log.error("Response body that failed to parse: {}", responseBody);
                    
                    // Return mock empty response to prevent UI crashes
                    JsonObject mockResponse = new JsonObject();
                    mockResponse.addProperty("hasActiveEvent", false);
                    mockResponse.add("activeEvents", new JsonArray());
                    return Optional.of(mockResponse);
                }
            }
            else if (response.code() == 401)
            {
                log.warn("JWT token expired, authentication required");
                isAuthenticated = false;
                configManager.setConfiguration("bingo", "isAuthenticated", false);
                return Optional.empty();
            }
            else if (response.code() == 404)
            {
                log.warn("Active events API endpoint not found (404). Check API configuration.");
                return Optional.empty();
            }
            else
            {
                log.warn("Failed to fetch active events: HTTP {} - {}", response.code(), response.message());
                if (response.body() != null)
                {
                    try 
                    {
                        String errorBody = response.body().string();
                        log.warn("Error response body: {}", errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody);
                    }
                    catch (Exception e)
                    {
                        log.warn("Could not read error response body", e);
                    }
                }
            }
        }
        catch (IOException e)
        {
            log.error("Network error during active events request", e);
        }
        catch (Exception e)
        {
            log.error("Unexpected error during active events request", e);
        }
        
        return Optional.empty();
    }
} 