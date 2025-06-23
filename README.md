# Bingo Plugin for RuneLite

A RuneLite plugin that integrates with the **wzd.bingo** website to provide real-time OSRS Bingo gameplay with team collaboration features.

## 🎯 What is Bingo?

OSRS Bingo is a competitive game mode where players race to complete objectives arranged in a grid format. Players can compete individually or in teams to achieve lines, patterns, or full board completion.

## ✨ Features

- **Automatic RSN Detection**: No need to manually enter your username - the plugin detects it when you're logged into OSRS
- **Secure Token Authentication**: Link your RuneLite client to your wzd.bingo account using website-generated tokens
- **Real-time Board Sync**: Your bingo board and progress sync automatically between the plugin and website
- **Team Collaboration**: View team member progress and coordinate strategies
- **Progress Tracking**: Automatic detection of completed objectives based on your in-game actions
- **Cross-platform**: Works seamlessly with the wzd.bingo web interface

## 🚀 Quick Start

### 1. Installation

1. Download the latest plugin JAR from the releases page
2. Place it in your RuneLite plugins folder
3. Restart RuneLite
4. The Bingo plugin will appear in your plugin list

### 2. Authentication Setup

1. **Create Account**: Visit [wzd.bingo](https://wzd.bingo) and create an account
2. **Generate Token**: 
   - Log into your wzd.bingo account
   - Navigate to "Plugin Integration" or "Generate Token"
   - Click "Generate New Token"
   - Copy the generated token (64-character string)
3. **Link Plugin**:
   - Launch OSRS and log into your account
   - Open the Bingo plugin panel (blue "B" icon in RuneLite sidebar)
   - Paste your token into the "Authentication Token" field
   - Click "Link Account"

### 3. Start Playing

Once authenticated, the plugin will:
- Automatically download your active bingo board
- Sync your team information
- Track objective completion in real-time
- Update your progress on the website

## 🔧 How It Works

### Authentication Flow

```
1. User creates account on wzd.bingo website
2. User generates authentication token on website
3. User enters token in RuneLite plugin
4. Plugin validates token with wzd.bingo API
5. Token is linked to user's RSN (auto-detected)
6. Plugin downloads board and team data
7. Real-time sync begins
```

### Data Synchronization

The plugin maintains a persistent connection with the wzd.bingo servers to:
- **Download**: Bingo boards, team rosters, current objectives
- **Upload**: Completed objectives, progress updates, location data
- **Real-time**: Team member progress, chat messages, strategy updates

## 🌐 Website Integration

### wzd.bingo Website Features

- **Board Creation**: Create custom bingo boards with various objective types
- **Team Management**: Form teams, invite players, manage permissions
- **Live Tracking**: Real-time view of all team member progress
- **Statistics**: Detailed analytics and completion history
- **Leaderboards**: Competition rankings and achievements

### API Endpoints Used

The plugin communicates with these wzd.bingo API endpoints:
- `POST /api/authenticate-token` - Token validation and account linking
- `GET /api/board/{id}` - Download bingo board data
- `GET /api/team/{id}` - Fetch team information
- `POST /api/progress` - Upload objective completion
- `WebSocket /ws/live` - Real-time updates

## 🔒 Security & Privacy

### Token Security
- **Unique Tokens**: Each token is cryptographically generated (256-bit entropy)
- **Expiration**: Tokens expire after 24-48 hours
- **Single Use**: Tokens can only be linked to one RSN
- **Revocation**: Tokens can be revoked instantly from the website

### Data Protection
- **Minimal Data**: Only necessary game data is transmitted
- **Encryption**: All communication uses HTTPS/WSS
- **Local Storage**: Sensitive data is stored securely in RuneLite config
- **No Passwords**: Plugin never handles your wzd.bingo password

## 🛠️ Configuration

### Plugin Settings

Access plugin settings through RuneLite's configuration panel:

- **Auto-sync Interval**: How often to sync with the server (default: 30 seconds)
- **Notification Settings**: Configure alerts for team updates
- **Privacy Mode**: Limit what data is shared with team members
- **Debug Logging**: Enable detailed logging for troubleshooting

### Config File Location
```
%USERPROFILE%\.runelite\settings.properties (Windows)
~/.runelite/settings.properties (Linux/Mac)
```

## 🎮 Gameplay Features

### Objective Types
- **Skill Levels**: Reach specific skill levels
- **Quest Completion**: Complete specific quests
- **Item Collection**: Obtain rare items or quantities
- **Location Visits**: Visit specific areas or landmarks
- **Achievement Diaries**: Complete diary tasks
- **Boss Kills**: Defeat specific bosses
- **Custom Objectives**: Community-created challenges

### Team Features
- **Progress Sharing**: See which objectives teammates are working on
- **Communication**: Built-in team chat and strategy planning
- **Role Assignment**: Assign objectives to specific team members
- **Coordination Tools**: Mark objectives as claimed or completed

## 📱 User Interface

### Plugin Panel
- **RSN Display**: Shows your current logged-in username
- **Token Input**: Secure field for authentication token
- **Connection Status**: Visual indicator of sync status
- **Board Overview**: Quick view of current objectives

### In-game Overlays
- **Objective Tracker**: Overlay showing current objectives
- **Progress Indicators**: Visual progress bars and completion status
- **Team Notifications**: Alerts when teammates complete objectives

## 🔧 Troubleshooting

### Common Issues

**"Must be logged in first"**
- Ensure you're logged into OSRS before opening the plugin panel
- The plugin needs to detect your RSN automatically

**"Authentication failed"**
- Check that your token is copied correctly (no extra spaces)
- Verify the token hasn't expired on the website
- Ensure the token hasn't been used with a different RSN

**"Connection failed"**
- Check your internet connection
- Verify wzd.bingo website is accessible
- Try generating a new token

### Debug Mode
Enable debug logging in plugin settings for detailed troubleshooting information.

### Support
- **Website**: Visit [wzd.bingo/support](https://wzd.bingo/support)
- **Discord**: Join our community Discord server
- **GitHub**: Report issues on the plugin repository

## 🔄 Updates

The plugin automatically checks for updates and will notify you when new versions are available. Updates include:
- New objective types
- Enhanced team features
- Bug fixes and performance improvements
- Security enhancements

## 📋 System Requirements

- **RuneLite**: Latest version recommended
- **Java**: 11 or higher
- **Internet**: Stable connection for real-time sync
- **Account**: Active wzd.bingo account

## 🤝 Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## 📄 License

This project is licensed under the MIT License - see [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- RuneLite team for the excellent plugin framework
- OSRS community for inspiration and feedback
- Beta testers who helped refine the plugin

---

**Happy Bingo-ing!** 🎯

For the latest updates and community discussions, visit [wzd.bingo](https://wzd.bingo)