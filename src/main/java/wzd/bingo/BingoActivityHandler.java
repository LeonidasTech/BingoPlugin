package wzd.bingo;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.ImageCapture;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemStack;
import okhttp3.*;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Singleton
public class BingoActivityHandler
{
    private static final String IMGUR_UPLOAD_URL = "https://api.imgur.com/3/image";
    
    private String imgurClientId = null;
    
    // Track recent kills to avoid duplicates
    private final Set<String> recentKills = new HashSet<>();
    
    @Inject
    private Client client;
    
    @Inject
    private BingoConfig config;
    
    @Inject
    private BingoService bingoService;
    
    @Inject
    private ConfigManager configManager;
    
    @Inject
    private ImageCapture imageCapture;
    
    @Inject
    private OkHttpClient httpClient;
    
    private boolean isParticipating = false;
    private String currentEventId = null;
    
    public void setParticipating(boolean participating, String eventId)
    {
        this.isParticipating = participating;
        this.currentEventId = eventId;
        log.info("Activity tracking set to: {} for event: {}", participating, eventId);
        
        // Initialize Imgur client ID if needed
        if (participating && imgurClientId == null)
        {
            initializeImgurClientId();
        }
    }
    
    private void initializeImgurClientId()
    {
        new Thread(() -> {
            try
            {
                imgurClientId = bingoService.getImgurClientId();
                if (imgurClientId != null)
                {
                    log.info("Successfully initialized Imgur client ID for screenshot uploads");
                }
                else
                {
                    log.warn("Failed to retrieve Imgur client ID - screenshot uploads will be disabled");
                }
            }
            catch (Exception e)
            {
                log.error("Error initializing Imgur client ID", e);
            }
        }).start();
    }
    
    @Subscribe
    public void onNpcLootReceived(NpcLootReceived npcLootReceived)
    {
        if (!isParticipating || currentEventId == null) return;
        
        NPC npc = npcLootReceived.getNpc();
        if (npc == null) return;
        
        String npcName = npc.getName();
        if (npcName == null) return;
        
        // Handle boss/raid completions
        if (isRaidCompletion(npcName))
        {
            handleRaidCompletion(npcName);
        }
        else if (isBoss(npcName))
        {
            handleBossKill(npcName);
        }
        else
        {
            handleMobKill(npcName);
        }
        
        // Check for valuable drops
        for (ItemStack item : npcLootReceived.getItems())
        {
            if (isValuableDrop(item))
            {
                handleValuableDrop(npcName, item);
            }
        }
    }
    
    @Subscribe
    public void onActorDeath(ActorDeath actorDeath)
    {
        if (!isParticipating || currentEventId == null) return;
        
        Actor actor = actorDeath.getActor();
        if (!(actor instanceof NPC)) return;
        
        NPC npc = (NPC) actor;
        String npcName = npc.getName();
        if (npcName == null) return;
        
        // Track the kill for later loot processing
        String killKey = npcName + "_" + System.currentTimeMillis();
        recentKills.add(killKey);
        
        log.debug("Tracked death: {}", npcName);
    }
    
    private void handleRaidCompletion(String raidName)
    {
        log.info("Raid completion detected: {}", raidName);
        
        // Submit raid completion (KC tracking only)
        submitActivity("RAID_COMPLETION", raidName, null, null);
    }
    
    private void handleBossKill(String bossName)
    {
        log.info("Boss kill detected: {}", bossName);
        
        // Submit boss kill (KC tracking only)
        submitActivity("BOSS_KILL", bossName, null, null);
    }
    
    private void handleMobKill(String mobName)
    {
        log.debug("Mob kill detected: {}", mobName);
        
        // Submit regular kill (KC tracking only)
        submitActivity("KILL", mobName, null, null);
    }
    
    private void handleValuableDrop(String npcName, ItemStack item)
    {
        String itemName = client.getItemDefinition(item.getId()).getName();
        log.info("Valuable drop detected: {} from {} (x{})", itemName, npcName, item.getQuantity());
        
        // Take screenshot and upload
        CompletableFuture.supplyAsync(() -> {
            try
            {
                // Get client's canvas for screenshot
                BufferedImage screenshot = new BufferedImage(
                    client.getCanvasWidth(),
                    client.getCanvasHeight(),
                    BufferedImage.TYPE_INT_RGB
                );
                
                // Create graphics and draw the canvas
                java.awt.Graphics2D g2d = screenshot.createGraphics();
                client.getCanvas().paint(g2d);
                g2d.dispose();
                
                if (screenshot != null)
                {
                    // Save locally first
                    String localPath = saveScreenshotLocally(screenshot, itemName);
                    
                    // Upload to imgur
                    String imgurUrl = uploadToImgur(screenshot);
                    
                    // Submit drop activity with screenshot
                    submitActivity("DROP", npcName, itemName, imgurUrl);
                    
                    return imgurUrl;
                }
            }
            catch (Exception e)
            {
                log.error("Failed to handle screenshot for drop: {}", itemName, e);
                // Submit drop without screenshot
                submitActivity("DROP", npcName, itemName, null);
            }
            return null;
        });
    }
    
    private String saveScreenshotLocally(BufferedImage screenshot, String itemName)
    {
        try
        {
            // Create directory structure: .runelite/bingo/event_id/
            Path bingoDir = Paths.get(System.getProperty("user.home"), ".runelite", "bingo", currentEventId);
            Files.createDirectories(bingoDir);
            
            // Create filename with timestamp
            String filename = String.format("%s_%s_%d.png", 
                itemName.replaceAll("[^a-zA-Z0-9]", "_"),
                config.rsn().replaceAll("[^a-zA-Z0-9]", "_"),
                System.currentTimeMillis());
            
            File file = bingoDir.resolve(filename).toFile();
            ImageIO.write(screenshot, "png", file);
            
            log.info("Screenshot saved locally: {}", file.getAbsolutePath());
            return file.getAbsolutePath();
        }
        catch (IOException e)
        {
            log.error("Failed to save screenshot locally", e);
            return null;
        }
    }
    
    private String uploadToImgur(BufferedImage screenshot)
    {
        // Check if we have a valid client ID
        if (imgurClientId == null || imgurClientId.isEmpty())
        {
            log.warn("No Imgur client ID available - skipping upload");
            return null;
        }
        
        try
        {
            // Convert image to base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(screenshot, "png", baos);
            String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());
            
            // Create form data
            RequestBody formBody = new FormBody.Builder()
                .add("image", base64Image)
                .add("type", "base64")
                .build();
            
            Request request = new Request.Builder()
                .url(IMGUR_UPLOAD_URL)
                .post(formBody)
                .addHeader("Authorization", "Client-ID " + imgurClientId)
                .build();
            
            try (Response response = httpClient.newCall(request).execute())
            {
                if (response.isSuccessful() && response.body() != null)
                {
                    String responseJson = response.body().string();
                    JsonObject jsonResponse = new com.google.gson.Gson().fromJson(responseJson, JsonObject.class);
                    
                    if (jsonResponse.get("success").getAsBoolean())
                    {
                        String imageUrl = jsonResponse.getAsJsonObject("data").get("link").getAsString();
                        log.info("Successfully uploaded screenshot to imgur: {}", imageUrl);
                        return imageUrl;
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.error("Failed to upload screenshot to imgur", e);
        }
        
        return null;
    }
    
    private void submitActivity(String activityType, String monsterName, String dropName, String screenshotUrl)
    {
        try
        {
            JsonObject activityData = new JsonObject();
            activityData.addProperty("rsn", config.rsn());
            activityData.addProperty("activityType", activityType);
            activityData.addProperty("monsterName", monsterName);
            activityData.addProperty("killCount", 1); // Each submission represents 1 kill
            activityData.addProperty("totalKc", 1); // This will be calculated on backend
            activityData.addProperty("teamId", Integer.parseInt(config.teamId())); // Add team ID
            
            if (dropName != null)
            {
                activityData.addProperty("dropName", dropName);
            }
            if (screenshotUrl != null)
            {
                activityData.addProperty("screenshotUrl", screenshotUrl);
            }
            
            String apiEndpoint = config.authApiUrl() + "/api/bingo/activity/" + currentEventId;
            
            RequestBody body = RequestBody.create(
                MediaType.get("application/json"),
                activityData.toString()
            );
            
            Request request = new Request.Builder()
                .url(apiEndpoint)
                .post(body)
                .addHeader("Authorization", "Bearer " + config.jwtToken())
                .addHeader("Content-Type", "application/json")
                .build();
            
            CompletableFuture.supplyAsync(() -> {
                try (Response response = httpClient.newCall(request).execute())
                {
                    if (response.isSuccessful())
                    {
                        log.info("Successfully submitted {} activity for {}", activityType, monsterName);
                        return true;
                    }
                    else
                    {
                        log.warn("Failed to submit activity: HTTP {}", response.code());
                        if (response.body() != null)
                        {
                            try
                            {
                                String errorBody = response.body().string();
                                log.warn("Error response: {}", errorBody);
                            }
                            catch (IOException ex)
                            {
                                log.warn("Could not read error response body");
                            }
                        }
                        return false;
                    }
                }
                catch (IOException e)
                {
                    log.error("Error submitting activity", e);
                    return false;
                }
            });
        }
        catch (Exception e)
        {
            log.error("Failed to create activity submission", e);
        }
    }
    
    private boolean isRaidCompletion(String npcName)
    {
        return npcName.contains("Maiden of Sugadinti") ||  // ToB
               npcName.contains("Xarpus") ||
               npcName.contains("Verzik Vitur") ||
               npcName.contains("Great Olm") ||              // CoX
               npcName.contains("Warden") ||                 // ToA
               npcName.contains("Tumeken");
    }
    
    private boolean isBoss(String npcName)
    {
        return npcName.equals("King Black Dragon") ||
               npcName.equals("Corporeal Beast") ||
               npcName.equals("Commander Zilyana") ||
               npcName.equals("General Graardor") ||
               npcName.equals("Kree'arra") ||
               npcName.equals("K'ril Tsutsaroth") ||
               npcName.equals("Kalphite Queen") ||
               npcName.equals("Chaos Elemental") ||
               npcName.equals("Zulrah") ||
               npcName.equals("Vorkath") ||
               npcName.equals("Alchemical Hydra") ||
               npcName.equals("The Nightmare") ||
               npcName.equals("Phosani's Nightmare") ||
               npcName.equals("Cerberus") ||
               npcName.equals("Abyssal Sire") ||
               npcName.equals("Kraken") ||
               npcName.equals("Thermonuclear Smoke Devil") ||
               npcName.contains("Dagannoth");
    }
    
    private boolean isValuableDrop(ItemStack item)
    {
        ItemComposition itemDef = client.getItemDefinition(item.getId());
        if (itemDef == null) return false;
        
        // Check if item is valuable (you can adjust these criteria)
        int value = itemDef.getPrice() * item.getQuantity();
        
        // Consider items worth more than 1M gp as valuable
        return value > 1000000 || 
               isRareItem(itemDef.getName()) ||
               isPetDrop(itemDef.getName());
    }
    
    private boolean isRareItem(String itemName)
    {
        return itemName.contains("Dragon warhammer") ||
               itemName.contains("Twisted bow") ||
               itemName.contains("Scythe") ||
               itemName.contains("Rapier") ||
               itemName.contains("Avernic") ||
               itemName.contains("Primordial") ||
               itemName.contains("Eternal") ||
               itemName.contains("Pegasian") ||
               itemName.contains("Armadyl") ||
               itemName.contains("Bandos") ||
               itemName.contains("Zamorak") ||
               itemName.contains("Saradomin") ||
               itemName.contains("Elysian") ||
               itemName.contains("Spectral") ||
               itemName.contains("Arcane");
    }
    
    private boolean isPetDrop(String itemName)
    {
        return itemName.toLowerCase().contains("pet") ||
               itemName.toLowerCase().contains("puppy") ||
               itemName.toLowerCase().contains("kitten") ||
               itemName.toLowerCase().contains("heron") ||
               itemName.toLowerCase().contains("beaver") ||
               itemName.toLowerCase().contains("squirrel");
    }
} 