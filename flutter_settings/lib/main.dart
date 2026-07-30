import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const GurmukhiKeyboardApp());
}

class GurmukhiKeyboardApp extends StatelessWidget {
  const GurmukhiKeyboardApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Gurmukhi Keyboard Settings',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        primarySwatch: Colors.orange,
        useMaterial3: true,
        fontFamily: 'Akhar',
      ),
      home: const HomeScreen(),
    );
  }
}

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Gurmukhi Keyboard', style: TextStyle(fontFamily: 'Akhar')),
        backgroundColor: Colors.orange,
        foregroundColor: Colors.white,
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          _buildMenuCard(
            context,
            'ਸ਼ਬਦ ਹਜਾਰੇ',
            Icons.menu_book,
            Colors.orange,
            () => Navigator.push(
              context,
              MaterialPageRoute(builder: (context) => const GurbaniReaderScreen(title: 'ਸ਼ਬਦ ਹਜਾਰੇ', filePath: '../app/src/main/assets/gurbani/shabad-hazare/content.txt')),
            ),
          ),
          _buildMenuCard(
            context,
            'Keyboard Themes',
            Icons.palette,
            Colors.blue,
            () => Navigator.push(
              context,
              MaterialPageRoute(builder: (context) => const ThemeSelectionScreen()),
            ),
          ),
          _buildMenuCard(
            context,
            'Enable Keyboard',
            Icons.keyboard,
            Colors.green,
            () => const MethodChannel('com.iqbal.gurmukhikeyboard/settings').invokeMethod('openImeSettings'),
          ),
        ],
      ),
    );
  }

  Widget _buildMenuCard(BuildContext context, String title, IconData icon, Color color, VoidCallback onTap) {
    return Card(
      elevation: 4,
      margin: const EdgeInsets.only(bottom: 16),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: color,
          child: Icon(icon, color: Colors.white),
        ),
        title: Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, fontFamily: 'Akhar')),
        trailing: const Icon(Icons.arrow_forward_ios, size: 16),
        onTap: onTap,
      ),
    );
  }
}

class ThemeSelectionScreen extends StatelessWidget {
  static const platform = MethodChannel('com.iqbal.gurmukhikeyboard/theme');

  const ThemeSelectionScreen({super.key});

  Future<void> _applyTheme(BuildContext context, String themeId) async {
    try {
      await platform.invokeMethod('changeTheme', {'theme': themeId});
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Theme "$themeId" applied successfully!')),
        );
      }
    } on PlatformException catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to apply theme: ${e.message}')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final List<Map<String, dynamic>> themes = [
      {'name': 'Light', 'color': Colors.white, 'id': 'light'},
      {'name': 'Dark', 'color': Colors.black, 'id': 'dark'},
      {'name': 'Kesari', 'color': Colors.orange, 'id': 'kesari'},
      {'name': 'Royal Blue', 'color': Colors.blue[900], 'id': 'royal_blue'},
      {'name': 'Green', 'color': Colors.green, 'id': 'green'},
      {'name': 'Red', 'color': Colors.red, 'id': 'red'},
    ];

    return Scaffold(
      appBar: AppBar(title: const Text('Select Theme', style: TextStyle(fontFamily: 'Akhar')), backgroundColor: Colors.orange),
      body: GridView.builder(
        padding: const EdgeInsets.all(16),
        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: 2,
          crossAxisSpacing: 16,
          mainAxisSpacing: 16,
        ),
        itemCount: themes.length,
        itemBuilder: (context, index) {
          final theme = themes[index];
          return GestureDetector(
            onTap: () => _applyTheme(context, theme['id']),
            child: Card(
              color: theme['color'],
              elevation: 4,
              child: Center(
                child: Text(
                  theme['name'],
                  style: TextStyle(
                    color: (theme['id'] == 'light' || theme['id'] == 'white') ? Colors.black : Colors.white,
                    fontWeight: FontWeight.bold,
                    fontFamily: 'Akhar',
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

class GurbaniReaderScreen extends StatefulWidget {
  final String title;
  final String filePath;

  const GurbaniReaderScreen({super.key, required this.title, required this.filePath});

  @override
  State<GurbaniReaderScreen> createState() => _GurbaniReaderScreenState();
}

class _GurbaniReaderScreenState extends State<GurbaniReaderScreen> {
  String _content = 'Loading...';

  @override
  void initState() {
    super.initState();
    _loadContent();
  }

  Future<void> _loadContent() async {
    try {
      final content = await rootBundle.loadString(widget.filePath);
      setState(() {
        _content = content;
      });
    } catch (e) {
      setState(() {
        _content = 'Error loading content: $e';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFFFFDE7),
      appBar: AppBar(
        title: Text(widget.title, style: const TextStyle(fontFamily: 'Akhar')),
        backgroundColor: Colors.orange,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20.0),
        child: Text(
          _content,
          textAlign: TextAlign.center,
          style: const TextStyle(
            fontSize: 22,
            height: 1.8,
            color: Colors.black87,
            fontFamily: 'Akhar',
          ),
        ),
      ),
    );
  }
}
