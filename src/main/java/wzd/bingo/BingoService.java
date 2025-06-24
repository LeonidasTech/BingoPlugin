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
    private static final int HEARTBEAT_INTERVAL_SECONDS = 300; // 5 minutes instead of 1 minute
    
    @Inject
    private BingoConfig config;
    
    @Inject
    private ConfigManager configManager;
    
    @Inject
    private OkHttpClient httpClient;
    
    private final Gson gson = new Gson();
    private ScheduledExecutorService heartbeatExecutor;
    private volatile boolean isAuthenticated = false;
    private volatile long lastHeartbeatTime = 0;
    
    // Callback for JWT expiration
    private Runnable jwtExpirationCallback;

    /**
     * Set callback to be invoked when JWT expires
     */
    public void setJwtExpirationCallback(Runnable callback)
    {
        this.jwtExpirationCallback = callback;
    }

    /**
     * Handle JWT expiration by clearing auth and triggering callback
     */
    private void handleJwtExpiration()
    {
        log.warn("Handling JWT token expiration - logging out user");
        isAuthenticated = false;
        configManager.setConfiguration("bingo", "jwtToken", "");
        configManager.setConfiguration("bingo", "isAuthenticated", false);
        
        // Stop background services
        stopHeartbeat();
        
        // Trigger callback to switch to auth panel
        if (jwtExpirationCallback != null)
        {
            SwingUtilities.invokeLater(jwtExpirationCallback);
        }
    }

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
                handleJwtExpiration();
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
                handleJwtExpiration();
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
                    handleJwtExpiration();
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
        
        // Prevent duplicate heartbeats within short time periods
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastHeartbeatTime < (HEARTBEAT_INTERVAL_SECONDS * 1000L * 0.8)) // 80% of interval
        {
            log.debug("Skipping heartbeat - too soon since last one");
            return;
        }
        
        // Create request body
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("rsn", rsn);
        requestBody.addProperty("timestamp", currentTime / 1000);
        
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
                lastHeartbeatTime = currentTime;
                log.debug("Heartbeat sent successfully for RSN: {} at {}", rsn, currentTime);
            }
            else if (response.code() == 401)
            {
                log.warn("JWT token expired during heartbeat");
                handleJwtExpiration();
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
     * Send heartbeat immediately on user activity
     */
    public void sendHeartbeatOnActivity()
    {
        // Send heartbeat immediately when user performs an action
        // but only if enough time has passed since last heartbeat
        sendHeartbeat();
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
                handleJwtExpiration();
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
    
    /**
     * Fetch activity log for a specific bingo event
     * @param bingoId The bingo event ID
     * @return Optional containing activity log data if successful
     */
    public Optional<JsonObject> fetchActivityLog(String bingoId)
    {
        String apiEndpoint = config.authApiUrl() + "/api/bingo/activity/" + bingoId + "?limit=50";
        
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
                log.debug("Activity log API response: {}", responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody);
                
                // Check if response is empty
                if (responseBody == null || responseBody.trim().isEmpty())
                {
                    log.warn("Empty response from activity log API");
                    return Optional.empty();
                }
                
                // Check if response is HTML (common when API returns error pages)
                String trimmedResponse = responseBody.trim();
                if (trimmedResponse.startsWith("<!") || trimmedResponse.startsWith("<html"))
                {
                    log.error("Activity log API returned HTML instead of JSON");
                    return Optional.empty();
                }
                
                // Try to parse as JSON
                try
                {
                    JsonObject activityData = gson.fromJson(responseBody, JsonObject.class);
                    log.debug("Successfully fetched activity log for bingo ID: {}", bingoId);
                    return Optional.of(activityData);
                }
                catch (Exception jsonException)
                {
                    log.error("Failed to parse JSON response from activity log API", jsonException);
                    return Optional.empty();
                }
            }
            else if (response.code() == 401)
            {
                log.warn("JWT token expired while fetching activity log");
                handleJwtExpiration();
                return Optional.empty();
            }
            else if (response.code() == 404)
            {
                log.warn("Activity log API endpoint not found (404) for bingo ID: {}", bingoId);
                return Optional.empty();
            }
            else
            {
                log.warn("Failed to fetch activity log: HTTP {} - {}", response.code(), response.message());
                return Optional.empty();
            }
        }
        catch (IOException e)
        {
            log.error("Network error during activity log request", e);
        }
        catch (Exception e)
        {
            log.error("Unexpected error during activity log request", e);
        }
        
        return Optional.empty();
    }
    
    /**
     * Check if the user is signed up for a specific bingo event
     * @param bingoId The bingo event ID
     * @return true if user is signed up, false otherwise
     */
    public boolean isSignedUpForEvent(String bingoId)
    {
        SignupStatus status = getSignupStatusForEvent(bingoId);
        return status.isSignedUp();
    }
    
    /**
     * Get detailed signup status for a specific bingo event
     * @param bingoId The bingo event ID
     * @return SignupStatus object with signup details
     */
    public SignupStatus getSignupStatusForEvent(String bingoId)
    {
        String apiEndpoint = config.authApiUrl() + "/api/bingo/signup/status/" + bingoId + "?rsn=" + config.rsn();
        
        Request request = new Request.Builder()
            .url(apiEndpoint)
            .get()
            .addHeader("Authorization", "Bearer " + config.jwtToken())
            .addHeader("Accept", "application/json")
            .build();
        
        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful() && response.body() != null)
            {
                String responseBody = response.body().string();
                log.debug("Signup status response length: {} chars", responseBody.length());
                
                // Check if response is HTML (common when API returns error pages or wrong endpoints)
                String trimmedResponse = responseBody.trim();
                if (trimmedResponse.startsWith("<!") || trimmedResponse.startsWith("<html"))
                {
                    log.warn("Signup status API returned HTML instead of JSON. This indicates the endpoint may not exist or there's a routing issue.");
                    log.warn("API endpoint used: {}", apiEndpoint);
                    log.warn("Response preview: {}", trimmedResponse.length() > 200 ? trimmedResponse.substring(0, 200) + "..." : trimmedResponse);
                    return new SignupStatus(false, false, "API returned HTML"); // Default to not signed up when API returns HTML
                }
                
                try
                {
                    JsonObject signupData = gson.fromJson(responseBody, JsonObject.class);
                    
                    if (signupData.has("signedUp"))
                    {
                        boolean isSignedUp = signupData.get("signedUp").getAsBoolean();
                        boolean isAccepted = signupData.has("accepted") ? signupData.get("accepted").getAsBoolean() : false;
                        String message = signupData.has("message") ? signupData.get("message").getAsString() : "";
                        
                        log.debug("Signup status for event {}: signedUp={}, accepted={}", bingoId, isSignedUp, isAccepted);
                        return new SignupStatus(isSignedUp, isAccepted, message);
                    }
                }
                catch (Exception jsonException)
                {
                    log.warn("Failed to parse signup status response as JSON: {}", responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody);
                    // Try to handle as primitive boolean response
                    if ("true".equalsIgnoreCase(responseBody.trim()) || "false".equalsIgnoreCase(responseBody.trim()))
                    {
                        boolean isSignedUp = Boolean.parseBoolean(responseBody.trim());
                        log.debug("Parsed signup status as primitive boolean: {}", isSignedUp);
                        return new SignupStatus(isSignedUp, false, ""); // Default accepted to false for primitive response
                    }
                }
            }
            else if (response.code() == 401)
            {
                log.warn("JWT token expired while checking signup status");
                handleJwtExpiration();
            }
            else if (response.code() == 404)
            {
                log.warn("Signup status API endpoint not found (404). Check if the endpoint '/api/bingo/signup/status/{}' is implemented on the backend.", bingoId);
                log.warn("Full URL attempted: {}", apiEndpoint);
            }
            else
            {
                log.warn("Failed to check signup status: HTTP {} - {}", response.code(), response.message());
                log.warn("API endpoint: {}", apiEndpoint);
                
                // Log response body for 500 errors to help with debugging
                if (response.code() == 500 && response.body() != null)
                {
                    try
                    {
                        String errorBody = response.body().string();
                        log.warn("Server error response body: {}", errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody);
                    }
                    catch (Exception e)
                    {
                        log.warn("Could not read error response body: {}", e.getMessage());
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.error("Error checking signup status for event {}", bingoId, e);
        }
        
        return new SignupStatus(false, false, "Unknown error"); // Default to not signed up
    }
    
    /**
     * Retrieve the Imgur client ID from the API
     * @return The Imgur client ID if successful, null otherwise
     */
    public String getImgurClientId()
    {
        String apiEndpoint = config.authApiUrl() + "/api/secrets/imgur_client_id";
        
        Request request = new Request.Builder()
            .url(apiEndpoint)
            .get()
            .addHeader("Authorization", "Bearer " + config.jwtToken())
            .addHeader("Accept", "application/json")
            .build();
        
        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful() && response.body() != null)
            {
                String responseBody = response.body().string();
                log.debug("Imgur client ID response length: {} chars", responseBody.length());
                
                // Check if response is HTML (common when API returns error pages or wrong endpoints)
                String trimmedResponse = responseBody.trim();
                if (trimmedResponse.startsWith("<!") || trimmedResponse.startsWith("<html"))
                {
                    log.warn("Imgur client ID API returned HTML instead of JSON. This indicates the endpoint may not exist or there's a routing issue.");
                    log.warn("API endpoint used: {}", apiEndpoint);
                    log.warn("Response preview: {}", trimmedResponse.length() > 200 ? trimmedResponse.substring(0, 200) + "..." : trimmedResponse);
                    return null; // No client ID available when API returns HTML
                }
                
                try
                {
                    JsonObject secretData = gson.fromJson(responseBody, JsonObject.class);
                    
                    if (secretData.has("success") && secretData.get("success").getAsBoolean() && secretData.has("value"))
                    {
                        String clientId = secretData.get("value").getAsString();
                        log.debug("Successfully retrieved Imgur client ID");
                        return clientId;
                    }
                }
                catch (Exception jsonException)
                {
                    log.warn("Failed to parse Imgur client ID response as JSON: {}", responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody);
                    // Try to handle as direct string response
                    String trimmed = responseBody.trim();
                    if (trimmed.length() > 0 && !trimmed.startsWith("{") && !trimmed.startsWith("[") && !trimmed.startsWith("<!"))
                    {
                        // Remove quotes if present
                        if (trimmed.startsWith("\"") && trimmed.endsWith("\""))
                        {
                            trimmed = trimmed.substring(1, trimmed.length() - 1);
                        }
                        log.debug("Parsed Imgur client ID as primitive string: {}", trimmed);
                        return trimmed;
                    }
                }
            }
            else if (response.code() == 401)
            {
                log.warn("JWT token expired while retrieving Imgur client ID");
                handleJwtExpiration();
            }
            else if (response.code() == 404)
            {
                log.warn("Imgur client ID API endpoint not found (404). Check if the endpoint '/api/secrets/imgur_client_id' is implemented on the backend.");
                log.warn("Full URL attempted: {}", apiEndpoint);
            }
            else
            {
                log.warn("Failed to retrieve Imgur client ID: HTTP {} - {}", response.code(), response.message());
                log.warn("API endpoint: {}", apiEndpoint);
                
                // Log response body for 500 errors to help with debugging
                if (response.code() == 500 && response.body() != null)
                {
                    try
                    {
                        String errorBody = response.body().string();
                        log.warn("Server error response body: {}", errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody);
                    }
                    catch (Exception e)
                    {
                        log.warn("Could not read error response body: {}", e.getMessage());
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.error("Error retrieving Imgur client ID", e);
        }
        
        return null;
    }
} 