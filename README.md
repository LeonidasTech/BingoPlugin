# Bingo Plugin for RuneLite

A RuneLite plugin that integrates with the **clan.bingo** platform for OSRS Bingo competitions. This plugin enables automatic tile completion detection, team progress tracking, and seamless authentication with the clan.bingo backend.

## Features

### 🔐 Authentication & Security
- **Discord ID Authentication**: Secure login using your Discord user ID
- **JWT Token Management**: Automatic token handling with refresh capabilities
- **Auto-RSN Detection**: Automatically detects your RuneScape username when logged in
- **Secure Configuration**: Sensitive data stored securely in RuneLite configuration

### 📊 Bingo Board Integration
- **Real-time Board Sync**: Automatically fetches your current bingo board state
- **Team Progress Tracking**: View your team's overall progress and member statistics
- **Tile Completion Detection**: Automatic detection of completed objectives
- **Evidence Submission**: Screenshot capture and submission for tile completions

### 🌐 Network Features
- **HTTP Client**: Full OkHttp integration with proper request/response handling
- **Heartbeat System**: Maintains connection with 60-second intervals
- **Network Resilience**: Automatic retry logic and graceful failure handling
- **Background Processing**: All network operations run on background threads

### 🎨 User Interface
- **clan.bingo Branding**: Professional UI with official branding
- **RuneLite Theme Integration**: Consistent with RuneLite's dark theme
- **Status Indicators**: Visual feedback for authentication and connection states
- **Responsive Design**: Compact layout optimized for the RuneLite sidebar

## Installation

1. **Download the Plugin**
   - Clone this repository or download the latest release
   - Place in your RuneLite plugins directory

2. **Build the Plugin** (if from source)
   ```bash
   ./gradlew build
   ```

3. **Enable in RuneLite**
   - Start RuneLite
   - Go to Configuration → Plugin Hub → Local Plugins
   - Enable the "Bingo" plugin

## Authentication Setup

### Step 1: Get Your Discord ID
1. Open Discord and go to User Settings (gear icon)
2. Go to Advanced → Enable Developer Mode
3. Right-click your username and select "Copy User ID"
4. Save this ID - it's your unique Discord identifier (17-19 digits)

### Step 2: Link Your Account
1. **Log into OSRS first** - Your RSN will be auto-detected
2. Open the Bingo plugin panel in RuneLite (sidebar)
3. Enter your Discord ID in the authentication form
4. Click "Link Account" to authenticate
5. Visit the profile page to manage your account settings

### Step 3: Join a Team
- Visit [clan.bingo](https://clan.bingo) to create or join a team
- Your team progress will automatically sync with the plugin

## API Integration

The plugin integrates with the clan.bingo backend through the following endpoints:

### Authentication
- **POST** `/api/auth/login` - Discord ID + RSN authentication
- Returns JWT token for subsequent requests

### Bingo Data
- **GET** `/api/bingo/board/:rsn` - Fetch user's bingo board
- **GET** `/api/bingo/team/:teamId` - Get team progress and statistics

### Tile Management
- **POST** `/api/bingo/submit` - Submit tile completion with evidence
- Includes screenshot, timestamp, and completion method

### Connection Management
- **POST** `/api/bingo/heartbeat` - Maintain active connection
- Sent every 60 seconds while authenticated

## Configuration

The plugin provides several configuration options:

### URLs
- **Profile URL**: Link to your clan.bingo profile page
- **API Base URL**: Backend API endpoint (configurable for different environments)

### Authentication
- **Discord ID**: Your Discord user identifier
- **RSN**: Auto-detected RuneScape username
- **JWT Token**: Automatically managed authentication token

## Security & Privacy

### Data Protection
- **Local Storage**: Sensitive data stored only in RuneLite's secure configuration
- **Encrypted Transport**: All API communications use HTTPS
- **Token Security**: JWT tokens are automatically managed and refreshed

### Privacy Features
- **No Chat Monitoring**: Plugin does not read or store chat messages
- **Minimal Data Collection**: Only collects necessary bingo completion data
- **User Control**: Complete control over what data is submitted

### Permission Model
- **Read-Only Game State**: Plugin only reads public game information
- **Screenshot Permission**: Only captures screenshots when tiles are completed
- **Network Access**: Only communicates with authorized clan.bingo servers

## Troubleshooting

### Authentication Issues
- **"Invalid Discord ID"**: Ensure your Discord ID is 17-19 digits long
- **"Authentication Failed"**: Verify your Discord ID is correct and try again
- **"Must be logged in first"**: Log into OSRS before attempting authentication

### Connection Problems
- **Network Errors**: Check your internet connection and firewall settings
- **API Timeouts**: The plugin will automatically retry failed requests
- **Token Expiration**: Authentication tokens refresh automatically

### Common Solutions
1. **Restart RuneLite** if the plugin panel doesn't appear
2. **Check Configuration** in RuneLite settings → Bingo
3. **Clear Authentication** by removing the Discord ID and re-authenticating
4. **Check Logs** in RuneLite console for detailed error information

## Development

### Requirements
- **Java 11+**
- **Gradle 8.0+**
- **RuneLite Development Environment**

### Building from Source
```bash
# Clone the repository
git clone https://github.com/your-username/BingoPlugin.git
cd BingoPlugin

# Build the plugin
./gradlew build

# Run with RuneLite (development)
./gradlew runClient
```

### Project Structure
```
src/main/java/wzd/bingo/
├── BingoPlugin.java          # Main plugin class
├── BingoConfig.java          # Configuration interface
├── BingoService.java         # HTTP client and API integration
└── ui/
    └── AuthPanel.java        # Authentication UI panel
```

### Contributing
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## API Documentation

### Request Headers
All authenticated requests include:
```
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

### Response Format
All API responses follow a consistent format:
```json
{
  "status": "ok",
  "data": { ... },
  "message": "Optional status message"
}
```

### Error Handling
- **401 Unauthorized**: Token expired, reauthentication required
- **429 Rate Limited**: Too many requests, automatic retry with backoff
- **500 Server Error**: Backend error, automatic retry with exponential backoff

## System Requirements

### Minimum Requirements
- **RuneLite**: Latest stable version
- **Java**: 11 or higher
- **Memory**: 512MB additional RAM for plugin operations
- **Network**: Stable internet connection for API communications

### Recommended
- **Java**: 17 or higher for optimal performance
- **Memory**: 1GB+ available RAM
- **Network**: Low-latency connection for real-time updates

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

### Getting Help
- **Documentation**: Check this README and inline code comments
- **Issues**: Report bugs on the [GitHub Issues](https://github.com/your-username/BingoPlugin/issues) page
- **Community**: Join the clan.bingo Discord for community support

### Contact
- **Plugin Issues**: GitHub Issues tracker
- **clan.bingo Platform**: Visit [clan.bingo](https://clan.bingo) for platform support
- **Discord**: Join the official clan.bingo Discord server

---

**Note**: This plugin is designed to work exclusively with the clan.bingo platform. Make sure you have an account on [clan.bingo](https://clan.bingo) before using this plugin.