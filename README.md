# اپلیکیشن اندروید چت بات صوتی هوش مصنوعی. مبتنی بر مدل زبانی Google Gemini. نوشته شده به زبان کانلین و طراحی رابط کاربری بر اساس Material Design 3.

## قابلیت ها:
پشتیبانی از زبان فارسی و انگلیسی.\
دارای tts زبان انگلیسی.\
دارای حالت های مختلف ورودی صدا مانند Voice Recognition, Voice Communitation جهت بهره مندی از قابلیت های Voice Activity Detection و Noise Suppression داخلی اندروید.\
قابلیت تنظیم Sample Rate ورودی صدا.\
بهره مندی از سیستم تم Material You پالت رنگی داینامیک بر اساس والپیپر دستگاه و حالت های روز و شب.\
دارای حالت Server Mode جهت ارسال صدا به سرور شخصی برای استفاده از LLM شخصی به جای Google Gemini.\

## راهنمای نصب و استفاده:
جهت تست میتوانید نسخه بیلد شده را از بخش release دانلود و نصب کنید.\
جهت بیلد گرفتن نسخه خودتان از کد منبع مراحل زیر را طی کنید:\
پیش‌نیازها:
اندروید استودیو: نسخه 2022.2.1 یا بالاتر.\
ریپازیتوری را کلون کنید و پروژه را در اندروید استودیو باز کنید.\
کلید API گوگل Gemini: کلید API خود را از Google AI Studio دریافت کنید. https://aistudio.google.com/apikey\
کلید API دریافت شده را در فایل MainActivity.kt جایگزین کنید. private var geminiAPIKey = "کلید API گوگل جمینی خود را اینجا قرار دهید"\
پروژه Firebase: یک پروژه Firebase ایجاد کرده و سپس یک service account به پروژه خود اضافه کنید و فایل کلید را دانلود کنید. https://console.firebase.google.com/\
کلید service account خود را به صورت یک فایل raw در مسیر raw/service_account.json قرار دهید.\

