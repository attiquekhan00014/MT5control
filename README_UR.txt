MT5 Control - Android APK (ورژن 3.0)
نیا ورژن 3: "Sign in with Google" فون کے براؤزر میں کھلتا ہے (Google ایپ کے اندر WebView میں لاگ ان نہیں دیتا) اور پھر خود ایپ میں واپس آ جاتا ہے۔ Paste کا بٹن بھی ایپ کی کلپ بورڈ سے key اٹھاتا ہے۔
اس کے لیے لیپ ٹاپ پر نئی UPDATE_ASSISTANT فائلیں (gsign والا حصہ) لگی ہونی چاہئیں۔

نیا کیا ہے: ایپ کے اندر اپنا الارم سروس۔ ایپ بند ہو تب بھی الارم کی اصلی آواز بجتی ہے (فون کی Alarm ٹون، لوپ میں، وائبریشن کے ساتھ) جب تک STOP نہ دبائیں۔
فون ری اسٹارٹ ہو تو سروس خود دوبارہ چلتی ہے۔ تصویر اپ لوڈ (چیٹ) بھی کام کرتی ہے۔

APK بنانے کا طریقہ (Android Studio کے بغیر، GitHub پر مفت):
 1) github.com پر فری اکاؤنٹ بنائیں -> New repository -> Private -> نام MT5Control۔
 2) "uploading an existing file" سے اس فولڈر کی ساری فائلیں اپلوڈ کریں (".github" فولڈر سمیت - وہ چھپا ہوتا ہے، زپ کھول کر سب کچھ سیلیکٹ کر کے ڈالیں)۔
 3) Actions ٹیب -> "Build APK" -> Run workflow۔ 3 سے 5 منٹ بعد رن کھول کر نیچے "MT5Control-apk" ڈاؤن لوڈ کریں۔ اس زپ کے اندر app-debug.apk ہے۔
 4) پرانی MT5 Control ایپ فون سے ان انسٹال کریں (ہر نئی بلڈ کی سائننگ مختلف ہوتی ہے، اوپر سے انسٹال نہیں ہو گی)۔
 5) APK فون میں بھیج کر انسٹال کریں (Unknown sources کی اجازت دینی ہو گی)۔
 6) ایپ کھولیں، لاگ اِن کریں، Price alarm میں جا کر الارم لگائیں۔ جب Notifications کی اجازت مانگے تو Allow دبائیں۔ "Send test" سے آواز چیک کریں۔

ضروری سیٹنگ (ورنہ بعض فون سروس بند کر دیتے ہیں): فون Settings -> Apps -> MT5 Control -> Battery -> "Unrestricted" / "Don't optimize" کریں۔
(Xiaomi/Oppo/Vivo/Samsung پر Autostart / Background activity بھی آن کریں۔)

شرط: الارم اسی وقت چیک ہوتے ہیں جب لیپ ٹاپ/سرور چل رہا ہو اور اس میں MT5 ٹرمنل لاگ اِن ہو۔
لنک بدلنا ہو تو app/build.gradle میں صرف APP_URL والی لائن بدلیں۔
Play Store کے لیے بعد میں signed AAB بنے گا (یہ debug APK صرف اپنے/ٹیسٹ کے لیے ہے)۔
