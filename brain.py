import os
import requests
import time
from pathlib import Path

OLLAMA_URL = "http://localhost:11434/api/generate"
OLLAMA_MODEL = "llama3.2"


def ask_llama(prompt):
    try:
        response = requests.post(
            OLLAMA_URL,
            json={
                "model": OLLAMA_MODEL,
                "prompt": prompt,
                "stream": False
            },
            timeout=120
        )

        data = response.json()

        if "response" in data:
            return data["response"]

        if "error" in data:
            return "Ollama error: " + data["error"]

        return "Sorry, Ollama did not return a proper response."

    except requests.exceptions.ConnectionError:
        return "Ollama is not running. Please start Ollama first."

    except Exception as e:
        return "Error: " + str(e)


def save_history(user_input, ai_reply):
    BASE_FOLDER = Path(__file__).resolve().parent
    history_folder = BASE_FOLDER / "Chat History"
    history_folder.mkdir(parents=True, exist_ok=True)
    # Java creates this file once when the app opens and passes it to Python.
    # This keeps the whole app session in one history file.
    session_file = os.environ.get("JARVIS_HISTORY_FILE", "").strip()

    if session_file:
        filename = Path(session_file)
        filename.parent.mkdir(parents=True, exist_ok=True)
    else:
        # Fallback for running backend.py directly without Java.
        timestamp = time.strftime("%Y-%m-%d_%H-%M-%S")
        filename = history_folder / f"chat_{timestamp}.txt"

    current_time = time.strftime("%Y-%m-%d %H:%M:%S")

    with open(filename, "a", encoding="utf-8") as f:
        f.write("Time: " + current_time + "\n")
        f.write("User:\n" + str(user_input) + "\n\n")
        f.write("AI:\n" + str(ai_reply) + "\n")
        f.write("-" * 60 + "\n\n")
