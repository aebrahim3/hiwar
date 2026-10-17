"""Role-play scenarios: Fo plays a character so practice feels like real life.

Each scenario tells Fo who to be, who you are, and what you're trying to get done.
Add your own by copying an entry; `id` is what the app sends.
"""

SCENARIOS = [
    {
        "id": "restaurant",
        "title": "Ordering at a restaurant",
        "icon": "restaurant",
        "description": "Order a meal, ask about dishes, and pay the bill.",
        "fo_role": "a friendly waiter at a busy Lebanese restaurant",
        "user_role": "a customer who has just sat down",
        "goal": "order a starter, a main dish and a drink, ask one question about the menu, then ask for the bill",
        "opening": "Greet me as I sit down and offer me the menu.",
    },
    {
        "id": "taxi",
        "title": "Taking a taxi",
        "icon": "taxi",
        "description": "Tell the driver where to go, agree on a price, and chat on the way.",
        "fo_role": "a chatty taxi driver in Amman",
        "user_role": "a passenger getting into the taxi",
        "goal": "give the destination, agree on the fare, and make small talk about the city",
        "opening": "Ask me where I'd like to go.",
    },
    {
        "id": "souq",
        "title": "Bargaining at the souq",
        "icon": "shopping",
        "description": "Ask prices and haggle for a better deal at a market stall.",
        "fo_role": "a shopkeeper selling scarves and spices in a traditional souq",
        "user_role": "a shopper looking for a gift",
        "goal": "ask about two items, ask the prices, and bargain the price down before buying",
        "opening": "Welcome me to your stall and show me something you think I'd like.",
    },
    {
        "id": "doctor",
        "title": "At the doctor's",
        "icon": "medical",
        "description": "Book an appointment and describe how you feel.",
        "fo_role": "a receptionist and then the doctor at a small clinic",
        "user_role": "a patient who feels unwell (a cold and a headache)",
        "goal": "check in, describe the symptoms and how long they've lasted, and understand the advice",
        "opening": "Greet me at the clinic reception and ask how you can help.",
    },
    {
        "id": "hotel",
        "title": "Checking into a hotel",
        "icon": "hotel",
        "description": "Check in, ask about the room, breakfast and Wi-Fi.",
        "fo_role": "a hotel receptionist",
        "user_role": "a guest arriving with a reservation",
        "goal": "check in, confirm the number of nights, and ask about breakfast time and the Wi-Fi",
        "opening": "Welcome me to the hotel and ask for my name.",
    },
    {
        "id": "directions",
        "title": "Asking for directions",
        "icon": "map",
        "description": "Find your way to the mosque, a station, or a café.",
        "fo_role": "a helpful local on the street",
        "user_role": "a visitor who is lost",
        "goal": "ask how to get to the nearest mosque, understand left/right/straight, and thank them",
        "opening": "Notice I look lost and ask if I need help.",
    },
    {
        "id": "neighbour",
        "title": "Meeting a new neighbour",
        "icon": "people",
        "description": "Introduce yourself, talk about family, work and hobbies.",
        "fo_role": "a new neighbour who just moved in next door",
        "user_role": "yourself, meeting them for the first time",
        "goal": "introduce yourself, ask about their family and work, and share your hobbies",
        "opening": "Introduce yourself as the new neighbour.",
    },
    {
        "id": "interview",
        "title": "Job interview",
        "icon": "work",
        "description": "Answer questions about your studies, experience and strengths.",
        "fo_role": "a polite interviewer at a technology company",
        "user_role": "a candidate interviewing for a junior developer role",
        "goal": "introduce yourself, describe your studies and one project, and say why you want the job",
        "opening": "Welcome me to the interview and ask me to introduce myself.",
    },
]

_BY_ID = {s["id"]: s for s in SCENARIOS}


def get(scenario_id: str | None) -> dict | None:
    return _BY_ID.get(scenario_id) if scenario_id else None


def public_list() -> list[dict]:
    """What the app shows in the scenario picker."""
    return [{k: s[k] for k in ("id", "title", "icon", "description")} for s in SCENARIOS]


def brief_block(scenario: dict) -> str:
    return (
        "This session is a ROLE-PLAY. Stay in character for the whole conversation.\n"
        f"You are {scenario['fo_role']}. I am {scenario['user_role']}.\n"
        f"My goal in the scene: {scenario['goal']}.\n"
        f"Start the scene yourself: {scenario['opening']}\n"
        "Make it feel real (small surprises are good), but keep it at my level. "
        "Only step out of character for the feedback at the end."
    )
