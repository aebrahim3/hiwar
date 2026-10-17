"""A fake Fo session so the whole app runs before you've signed in to Fo."""

import asyncio

from . import config

MOCK_TRANSCRIPT = """Fo: مرحباً! كيف كان يومك؟
Me: الحمد لله، كان يوماً جميلاً. ذهبت إلى الجامعة.
Fo: جميل! ماذا درست اليوم؟
Me: درست البرمجة. أنا أحب البرمجة كثير.
Fo: رائع! وماذا ستفعل في عطلة نهاية الأسبوع؟
Me: سأذهب مع أصدقائي إلى... امم... إلى المطعم لبناني.
Fo: أي طعام تحب في المطعم اللبناني؟
Me: أحب الفلافل والحمص. هو لذيذ جداً.
Fo: (in English) Great session! Here's your breakdown...
Fo: Do you want to save this session for later reference?
Me: Yes, save it."""

MOCK_BREAKDOWN = {
    "saved": True,
    "summary": "You kept the conversation going for the whole session and answered every question in "
               "full sentences. Your past-tense verbs were solid; adjective agreement needs work.",
    "fluency": "Steady pace with full-sentence answers. One long hesitation before naming the restaurant, "
               "but you stayed in Arabic instead of switching to English.",
    "mistakes": [
        {"said": "أحب البرمجة كثير", "correction": "أحب البرمجة كثيراً", "category": "grammar",
         "explanation": "As an adverb ('a lot'), كثير takes the accusative ending: كثيراً.",
         "example": "أقرأ كثيراً في المساء."},
        {"said": "إلى المطعم لبناني", "correction": "إلى المطعم اللبناني", "category": "grammar",
         "explanation": "An adjective must match its noun's definiteness: المطعم → اللبناني.",
         "example": "سكنتُ في البيت الكبير."},
        {"said": "الفلافل والحمص. هو لذيذ", "correction": "الفلافل والحمص. هما لذيذان", "category": "grammar",
         "explanation": "Two things need the dual pronoun and a dual adjective: هما لذيذان.",
         "example": "الكتابان جديدان."},
    ],
    "highlights": [
        "Correct كان + accusative: كان يوماً جميلاً",
        "Correct future tense with سـ: سأذهب",
        "You recovered smoothly after hesitating instead of switching to English",
    ],
    "improvements": [
        "Practise adverbs with the -اً ending (كثيراً، جداً، دائماً)",
        "Review dual pronouns and adjectives (هما، لذيذان)",
        "Try longer answers: add a reason with لأنّ",
    ],
    "vocabulary": [
        {"word": "عطلة نهاية الأسبوع", "meaning": "the weekend", "note": "Literally 'the break at the end of the week'."},
        {"word": "لذيذ", "meaning": "delicious", "note": "Dual: لذيذان; feminine: لذيذة."},
    ],
    "next_focus": "Adjective agreement: definiteness, gender and the dual.",
}


async def mock_session(session: dict, progress) -> tuple[str, dict, bool]:
    steps = [
        ("calling", "(mock) Fo is dialing your phone"),
        ("in_call", f"(mock) On the call with Fo ({session['duration_minutes']} min session)"),
        ("in_call", "(mock) Fo is giving you your spoken breakdown"),
        ("summarizing", "(mock) Getting Fo's breakdown"),
    ]
    for status, text in steps:
        progress(text, status)
        await asyncio.sleep(config.MOCK_SECONDS / len(steps))
    return MOCK_TRANSCRIPT, MOCK_BREAKDOWN, True
