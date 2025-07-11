# Disclaimer

Some aspect of the project might not work since the UI is not implemented yet and will be added to the project later in the future, right now this project only contains the backend logic!!!!

# JDown

A modern Internet Download Manager (IDM) clone built with Java and JavaFX, providing fast and reliable file downloads with an intuitive user interface.

## 🚀 Features

- **Multi-threaded Downloads**: Accelerate download speeds by splitting files into multiple segments
- **Resume Capability**: Resume interrupted downloads from where they left off
- **Modern UI**: Clean and intuitive JavaFX-based user interface

## 📋 Prerequisites

Before running JDown, ensure you have the following installed:

- **Java 17 or higher**: [Download Java](https://www.oracle.com/java/technologies/javase-downloads.html)
- **JavaFX**: If not included with your Java installation
- **Maven**: For building the project (if building from source)

## 🛠️ Installation

### Option 1: Build from Source
1. Clone the repository:
   ```bash
   git clone https://github.com/Sina-karimi81/JDown.git
   cd JDown
   ```

2. Build the project:
   ```bash
   mvn clean compile
   ```

3. Run the application:
   ```bash
   mvn javafx:run
   ```

## 🎯 Usage

### Basic Download
1. Launch JDown
2. Click "Add Download"
3. Enter the URL of the file you want to download
4. Choose destination folder and filename
5. Click "Start Download"

## 🏗️ Architecture

```
JDown/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── com/jdown/
│   │   │   │   ├── common/         # Utility classes
│   │   │   │   ├── configuration/  # Configuration management classes
│   │   │   │   ├── database/       # Database logic
│   │   │   │   ├── dataObjects/    # Objects representing the data
│   │   │   │   ├── download/       # Download logic
│   │   │   │   ├── exception/      # Exception classes
│   │   │   │   ├── serialization/  # Jackson custom serialization logic
│   │   │   │   └── Main.java       # Application entry point
│   │   │   └── module-info.java    # Module descriptor
│   │   └── resources/
│   │   │    ├── jdown/             # JavaFX FXML files
│   │   └── configuration.json      # project configs
│   │   └── logback.xml             # logback config
│   └── test/                       # Unit tests
├── pom.xml                         # Maven configuration
└── README.md
```

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

### Development Setup
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style
- Follow Java naming conventions
- Use meaningful variable and method names
- Add comments for complex logic
- Ensure all tests pass before submitting

## 🐛 Issues and Bug Reports

If you encounter any issues or bugs, please report them on the [Issues](https://github.com/Sina-karimi81/JDown/issues) page with:
- A clear description of the problem
- Steps to reproduce
- Your operating system and Java version
- Screenshots if applicable

## 🎯 Roadmap

- [ ] Add UI panel
- [ ] Support for download scheduling
- [ ] Support for Batch Downloads
- [ ] Integration with cloud storage services
- [ ] Torrent download support
- [ ] FTP download support
- [ ] Chrome/Firefox extension
- [ ] Themes and customization options
- [ ] Command-line interface

## 🙏 Acknowledgments

- Inspired by Internet Download Manager (IDM)
- Built with [JavaFX](https://openjfx.io/)
- Icons from [Icons8](https://icons8.com/)
- Removed boilerplate code with [Lombok](https://github.com/projectlombok/lombok)
- Used JUnit Jupiter, WireMock and Mockito for Testing

## 📞 Support

If you need help or have questions:
- Check the [Wiki](https://github.com/Sina-karimi81/JDown/wiki) for detailed documentation
- Open an issue for bugs or feature requests

---

**Made with ❤️ by [Sina Karimi](https://github.com/Sina-karimi81)**
