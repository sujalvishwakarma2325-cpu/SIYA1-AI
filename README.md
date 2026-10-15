# Siya AI (Android)

Voice se chalne wala personal assistant. Aap bolte ho, Siya Claude se samajhkar phone par kaam karti hai.

## Kya kar sakti hai (v0.1)

- Apps kholna, link/search kholna, navigation shuru karna
- Call aur SMS (pehle aapse poochti hai)
- Alarm, timer, calendar event, notes
- Torch, volume, brightness, music play/pause/next
- Battery aur samay batana, settings pages kholna (Wi-Fi, Bluetooth...)
- Hindi, Hinglish aur English me baat

## Khud ko badalna (aapki ijazat se)

Siya apni settings (naam, bhasha, bolne ka tareeka) aur nayi "skills" badal sakti hai.
Skill matlab ek routine, jaise "good night" = alarm + volume kam + torch off.
Har badlav se pehle ek dialog aata hai: "Kya main ye karun?" Aap Haan dabaoge tabhi lagu hota hai.
Agar Siya ko koi kaam nahi aata, to wo khud puchegi ki kya naya skill jod de.

Dhyan do: Android app khud apna Kotlin code badalkar naya app nahi bana sakta.
Isliye "khud ko badalna" skills aur settings ke roop me hai. Bade naye feature ke liye mujhse (Claude se) kehna, main code badal dunga.

## Build kaise karein

### Tareeka A: GitHub se (sirf browser chahiye)
1. GitHub par nayi repo banao (jaise `siya-ai`).
2. Is folder ke saare files usme upload karo (`.github/workflows/build.yml` zaroor).
3. Repo ke **Actions** tab me "Build Siya APK" chalega (5-10 minute).
4. Run khulkar niche **Artifacts** se `siya-ai-apk` download karo, zip kholkar `app-debug.apk` install karo.
   (Phone me "Unknown apps se install" allow karna padega.)

### Tareeka B: Android Studio (computer par)
Folder ko Android Studio me kholo, Run dabao.

## Pehli baar chalane par

1. Settings me **Claude API key** daalo (console.anthropic.com se milti hai; API ka kharcha alag se lagta hai).
2. Mic, Call, SMS, Contacts ki permissions Allow karo.
3. Brightness ke liye "Modify system settings" Siya ko allow karo (Siya khud us page par le jayegi).

## Abhi nahi hai (agle step)

- Hamesha sunna / "Hey Siya" bolkar jagana (foreground service chahiye)
- WhatsApp message bhejna, doosre apps ke andar tap karna (Accessibility Service chahiye)
- Notifications padhna

Ye sab ho sakte hain, par inme zyada permissions aur testing lagti hai.

## Suraksha

- API key sirf aapke phone me rehti hai aur sirf api.anthropic.com ko jaati hai.
- Call, SMS, notes mitana, aur har self-change se pehle aapse poochha jaata hai.
