import 'dart:async';
import 'package:flutter/services.dart';

class DictionaryService {
  static const platform = MethodChannel('com.iqbal.gurmukhikeyboard/dictionary');

  // ਪੰਜਾਬੀ ਦੇ ਮੁੱਖ ਸ਼ਬਦਾਂ ਦੀ ਲਿਸਟ (ਇਹ ਇੱਕ ਨਮੂਨਾ ਹੈ, ਅਸਲੀ ਵਿੱਚ ਅਸੀਂ 50,000+ ਸ਼ਬਦ ਵਰਤਾਂਗੇ)
  static const List<String> commonWords = [
    "ਸਤਿਨਾਮ", "ਵਾਹਿਗੁਰੂ", "ਪੰਜਾਬ", "ਗੁਰਮੁਖੀ", "ਕੀਬੋਰਡ", "ਅੰਮ੍ਰਿਤਸਰ", "ਸੇਵਾ", "ਸਿਮਰਨ"
  ];

  // Android ਕੀਬੋਰਡ ਨੂੰ ਨਵੇਂ ਸ਼ਬਦ ਭੇਜਣ ਲਈ
  Future<void> syncDictionaryToKeyboard(List<String> words) async {
    try {
      await platform.invokeMethod('updateDictionary', {'words': words});
    } on PlatformException catch (e) {
      print("Failed to sync dictionary: ${e.message}");
    }
  }

  // Gurbani Dictionary ਡਾਊਨਲੋਡ ਕਰਨ ਦਾ ਫੰਕਸ਼ਨ
  Future<List<String>> downloadGurbaniDictionary() async {
    // ਇੱਥੇ ਅਸੀਂ ਕਿਸੇ ਸਰਵਰ ਤੋਂ ਸ਼ਬਦ ਡਾਊਨਲੋਡ ਕਰ ਸਕਦੇ ਹਾਂ
    await Future.delayed(const Duration(seconds: 2)); // ਸਿਮੂਲੇਸ਼ਨ
    return ["ੴ", "ਸਤਿਗੁਰ", "ਪ੍ਰਸਾਦਿ", "ਜਪੁ", "ਨਾਨਕ"];
  }
}
