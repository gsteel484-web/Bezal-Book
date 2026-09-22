# हिसाब • Android ledger application

यह एक ऑफलाइन, एक-उपयोगकर्ता हिसाब-किताब ऐप का स्रोत प्रोजेक्ट है। इसे APK या production-certified release न समझें। इस कार्यस्थल में Android SDK, Gradle और Chromium उपलब्ध नहीं थे, इसलिए APK build, device tests और visual UI tests नहीं चल सके।

## खोलना और APK बनाना

1. Android Studio में इस `Hisaab` फ़ोल्डर को खोलें।
2. JDK 17, Android SDK 35 और Gradle 8.9 स्थापित करें। Android Gradle Plugin 8.7.3 उपयोग होता है। Internet से पहली बार build dependencies चाहिए।
3. Gradle executable को PATH में रखें और project root से `gradle :app:assembleDebug` चलाएँ। यदि Android Studio wrapper माँगे तो पहले `gradle wrapper --gradle-version 8.9` चलाएँ। Gradle wrapper binary इस archive में शामिल नहीं है।
4. APK का पथ: `app/build/outputs/apk/debug/app-debug.apk`। Android 8.0 / API 26 या ऊपर के परीक्षण फ़ोन में इंस्टॉल करें।
5. GitHub पर यही root रखने पर `.github/workflows/android.yml` से manual build भी किया जा सकता है। यह workflow दिया गया है, चलाया नहीं गया। Play Store प्रकाशन के लिए अपना release signing key चाहिए।

ब्राउज़र में रूप और उदाहरण देखने के लिए `PREVIEW.html` खोलें। इसका डेटा अलग browser-local storage में रहता है। सेटिंग्स में “उदाहरण देखें” केवल खाली preview में उपलब्ध है। Android ऐप कोई डेमो डेटा नहीं भरता। कुछ browsers local files पर storage रोक सकते हैं; उस स्थिति में `python3 -m http.server 8765 --directory app/src/main/assets` और `http://localhost:8765` उपयोग करें।

## शामिल काम

- हिंदी / Hinglish-friendly UI, light/dark/system theme, छोटे स्क्रीन का bottom navigation और बड़े स्क्रीन की sidebar।
- आज की जमा, बाकी, कुल लेन-देन, एंट्री संख्या, कुल net balance, non-zero balance वाले खाते, हाल की एंट्री।
- नाम, मोबाइल, शुरुआती बैलेंस, नोट्स, active/inactive, pinned parties और recent/name/balance sorting।
- पार्टी → नई एंट्री → रकम → जमा/बाकी → सहेजें। तारीख और वैकल्पिक description/payment mode/reference/notes।
- इतिहास सहित immutable ledger, opening carry-forward, running balances और तारीख/खोज/क्रम फ़िल्टर।
- कारण के साथ reversal; दूसरी reversal रोकना; settlement और adjustment entries का इतिहास।
- दैनिक, पार्टीवार, मासिक PDF रिपोर्ट, पार्टी स्टेटमेंट और audit history।
- Android native PDF generation, Android share sheet और WhatsApp direct share target। भेजने से पहले उपयोगकर्ता WhatsApp में प्राप्तकर्ता चुनता है। WhatsApp उपलब्ध न हो तो सामान्य share sheet आती है। कोई संदेश अपने आप नहीं भेजा जाता।
- उपयोगकर्ता द्वारा चुना हुआ folder: नई entry/reversal के बाद संबंधित पार्टी का पूरा PDF और हर manually generated PDF अपने आप save होता है। यह periodic scheduling नहीं है।
- पूरा logical database JSON backup: सभी parties, entries और audit rows। फ़ोल्डर की चयन अनुमति app settings में रहती है; reinstall के बाद दोबारा चुननी पड़ती है। Theme फ़ोन की स्थानीय UI preference है, database backup का हिस्सा नहीं है।
- Fresh/empty installation में transactional restore, version/type/date/amount/FK checks। Existing data कभी silently replace या merge नहीं होता।

## हिसाब का अर्थ

`Closing = Opening + Baaki − Jama`

Positive balance: पार्टी से लेने हैं। Negative balance: पार्टी को देने हैं। Zero: हिसाब बराबर। Settlement/adjustment उसी नियम की auditable entry है; पैसा भेजने का फीचर नहीं है। All-time totals में opening entries भी शामिल हैं; filtered period के बाहर का balance opening carry-forward होता है। Search केवल दिखने वाली rows को filter करती है; statement totals या running balance नहीं बदलते। Entry timestamps UTC, transaction dates स्थानीय business date हैं।

SQLite में integer paise इस्तेमाल होते हैं। प्रति entry अधिकतम ₹1,00,00,00,000; कुल gross book amount JavaScript के exact integer range से नीचे सीमित है। `posting` view हर entry से दो बराबर/विपरीत postings बनाता है: party receivable/payable और ledger clearing (opening के लिए opening equity)। Opening reversal अपना वही equity contra उपयोग करता है। यह customer-ledger subledger है; inventory, GST, bank reconciliation या पूर्ण general-ledger financial statements का दावा नहीं है।

Entries update/delete नहीं होतीं; SQL triggers इसे रोकते हैं। Audit भी immutable है। Entry और audit एक transaction में commit होते हैं। Correction: गलत entry पर कारण सहित reversal, फिर सही नई entry। Created by अभी `Owner` है: multi-user authentication शामिल नहीं है।

## संरचना

- `app/src/main/assets/`: responsive local UI, calculation core, वास्तविक SQLite schema।
- `LedgerDb.java`: SQLite persistence, validation, immutable entries और atomic restore।
- `MainActivity.java`: restricted local WebView bridge, SAF folder picker, PDF generation और sharing।
- `ReportProvider.java`: read-only, non-exported, temporary URI grants वाला PDF provider।
- Native app में INTERNET और broad storage permissions नहीं माँगी जातीं। UI केवल bundled local assets load करती है; arbitrary navigation blocked है।
- Backup JSON encrypted नहीं है। सुरक्षित folder में रखें। Local database Android private app storage में है; अतिरिक्त PIN/biometric lock और application-level encryption शामिल नहीं है।

## परीक्षण और जारी करने से पहले बाकी काम

पास: 9 JavaScript accounting tests और 7 real SQLite schema tests। तीन Java source files का compiler-parser syntax validation पास हुआ; यह Android compilation/type checking नहीं है। JS syntax check पास है।

`node tests/core.test.cjs`

`python3 tests/database_test.py`

`tests/ui.test.cjs` में mobile workflow test मौजूद है, लेकिन Chromium binary अनुपलब्ध होने से नहीं चला। Playwright और Chromium स्थापित करके preview server के साथ `node tests/ui.test.cjs` चलाएँ। Screenshot paths इसी project के tests directory में बनते हैं।

रिलीज़ से पहले ये device checks जरूरी हैं:

- APK compilation, Android 8/11/15 startup, WebView bridge और system insets।
- Keyboard, Hindi rendering, accessibility, light/dark और small screens।
- Hindi/long descriptions वाली multi-page PDF rendering और share URI access।
- SAF folder save, revoked permissions, full storage और cloud-provider failures।
- JSON backup → uninstall → install → restore; corrupted/duplicate backups और rollback।
- WhatsApp installed / missing flows।
- बड़ी history performance: अभी UI एक पूरा snapshot load करती है। High-volume deployment से पहले pagination, indexed aggregate queries और performance profiling करें।

PDF errors entry को वापस नहीं मिटाते; उपयोगकर्ता को save failure बताया जाता है। डेटाबेस बैकअप सफल होने पर ही last-backup समय बदलता है। Complete restore के लिए JSON चाहिए; केवल PDF पर्याप्त नहीं है।

Android API references consulted:
- https://developer.android.com/training/data-storage/shared/documents-files
- https://developer.android.com/reference/android/graphics/pdf/PdfDocument
