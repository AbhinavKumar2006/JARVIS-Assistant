import requests
import os
import time
import queue
import sounddevice as sd
import vosk
import json
import threading
import re
from pypdf import PdfReader
from pathlib import Path
import cv2
import pytesseract
import pyttsx3
import subprocess
import winsound


# -------------------------------------------------
# SETTINGS
# -------------------------------------------------

WAKE_WORD = "jarvis"

BASE_FOLDER = Path(__file__).resolve().parent

MODEL_PATH = BASE_FOLDER / "vosk-model-small-en-us-0.15"
UPLOAD_FOLDER = BASE_FOLDER / "Upload File"
HISTORY_FOLDER = BASE_FOLDER / "Chat History"

LOCAL_TESSERACT_PATH = BASE_FOLDER / "Tesseract-OCR" / "tesseract.exe"
SYSTEM_TESSERACT_PATH = Path(r"C:\Program Files\Tesseract-OCR\tesseract.exe")

if LOCAL_TESSERACT_PATH.exists():
    TESSERACT_PATH = LOCAL_TESSERACT_PATH
    pytesseract.pytesseract.tesseract_cmd = str(LOCAL_TESSERACT_PATH)

elif SYSTEM_TESSERACT_PATH.exists():
    TESSERACT_PATH = SYSTEM_TESSERACT_PATH
    pytesseract.pytesseract.tesseract_cmd = str(SYSTEM_TESSERACT_PATH)

else:
    TESSERACT_PATH = None
    print("WARNING: Tesseract OCR not found. Image text reading will not work.")


OLLAMA_URL = "http://localhost:11434/api/generate"
OLLAMA_MODEL = "llama3.2"

VOICE_NUMBER = 0       # 0 = first Windows voice, 1 = second voice if available
VOICE_RATE = 175
VOICE_VOLUME = 1.0

q = queue.Queue()
running = True
text_mode_active = False
engine_lock = threading.Lock()


# -------------------------------------------------
# CHECK REQUIRED FOLDERS / MODEL
# -------------------------------------------------

UPLOAD_FOLDER.mkdir(parents=True, exist_ok=True)
HISTORY_FOLDER.mkdir(parents=True, exist_ok=True)

if not MODEL_PATH.exists():
    print("ERROR: Vosk model folder not found.")
    print("Check this path:")
    print(MODEL_PATH)
    raise SystemExit

try:
    model = vosk.Model(str(MODEL_PATH))
except Exception as e:
    print("ERROR: Failed to load Vosk model.")
    print(e)
    raise SystemExit


# -------------------------------------------------
# MICROPHONE CALLBACK
# -------------------------------------------------

def callback(indata, frames, time_info, status):
    if status:
        print(status)
    q.put(bytes(indata))


# -------------------------------------------------
# CLEAN TEXT BEFORE SPEAKING
# -------------------------------------------------

def clean_for_speech(text):
    text = str(text)
    text = re.sub(r"[*_#>`\\[\\]{}|~]", "", text)
    text = text.replace("•", ". ")
    text = text.replace("-", ". ")
    text = re.sub(r"http\S+|www\S+|ftp\S+", "", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()


# -------------------------------------------------
# SPEAK FUNCTION: CREATE WAV, PLAY, DELETE
# -------------------------------------------------

def speak(text):
    text_to_speak = clean_for_speech(text)

    if not text_to_speak:
        return

    print("\nJARVIS:", text)

    filename = os.path.abspath("jarvis_reply.wav")

    with engine_lock:
        try:
            if os.path.exists(filename):
                try:
                    os.remove(filename)
                except PermissionError:
                    pass

            local_engine = pyttsx3.init("sapi5")
            local_engine.setProperty("rate", VOICE_RATE)
            local_engine.setProperty("volume", VOICE_VOLUME)

            voices = local_engine.getProperty("voices")
            if voices and len(voices) > VOICE_NUMBER:
                local_engine.setProperty("voice", voices[VOICE_NUMBER].id)

            local_engine.save_to_file(text_to_speak, filename)
            local_engine.runAndWait()
            local_engine.stop()

            for _ in range(50):
                if os.path.exists(filename) and os.path.getsize(filename) > 1000:
                    break
                time.sleep(0.1)

            if not os.path.exists(filename):
                print("ERROR: WAV file was not created.")
                return

            if os.path.getsize(filename) < 1000:
                print("ERROR: WAV file is empty.")
                return

            winsound.PlaySound(filename, winsound.SND_FILENAME)
            winsound.PlaySound(None, winsound.SND_PURGE)

            try:
                os.remove(filename)
            except PermissionError:
                time.sleep(0.3)
                try:
                    os.remove(filename)
                except Exception:
                    print("Audio played, but file could not be deleted.")

        except Exception as e:
            print("Speak error:", e)


# -------------------------------------------------
# START OLLAMA IF NEEDED
# -------------------------------------------------

def ensure_ollama_running():
    try:
        requests.get("http://localhost:11434", timeout=2)
        return True
    except requests.exceptions.RequestException:
        try:
            subprocess.Popen(
                ["ollama", "serve"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                shell=True
            )
            time.sleep(3)
            return True
        except Exception as e:
            print("Could not start Ollama automatically:", e)
            return False


# -------------------------------------------------
# ASK OLLAMA
# -------------------------------------------------

def ask_llama(prompt):
    if not ensure_ollama_running():
        return "Ollama is not running, and I could not start it automatically. Please start Ollama and try again."

    try:
        response = requests.post(
            OLLAMA_URL,
            json={
                "model": OLLAMA_MODEL,
                "prompt": prompt,
                "stream": False
            },
            timeout=180
        )

        if response.status_code != 200:
            return f"Ollama error {response.status_code}: {response.text}"

        data = response.json()
        return data.get("response", "No response received from Ollama.").strip()

    except requests.exceptions.ConnectionError:
        return "Cannot connect to Ollama. Please run: ollama serve"
    except requests.exceptions.Timeout:
        return "Ollama took too long to reply. Try a shorter question or a lighter model."
    except Exception as e:
        return "Error asking Ollama: " + str(e)


# -------------------------------------------------
# AUDIO QUEUE HELPERS
# -------------------------------------------------

def clear_audio_queue():
    while not q.empty():
        try:
            q.get_nowait()
        except queue.Empty:
            break


# -------------------------------------------------
# LISTEN ONE SENTENCE
# -------------------------------------------------

def listen_sentence():
    rec = vosk.KaldiRecognizer(model, 16000)

    while running:
        data = q.get()

        if rec.AcceptWaveform(data):
            result = json.loads(rec.Result())
            text = result.get("text", "").lower().strip()

            if text:
                print("You:", text)
                return text

    return ""


# -------------------------------------------------
# VOICE CHAT MODE
# -------------------------------------------------

def voice_chat_mode():
    global running

    speak("Voice chat mode activated. Say stop to return to main mode. Say exit assistant to close everything.")
    clear_audio_queue()

    while running:
        print("\nListening...")
        user_input = listen_sentence()

        if not user_input:
            continue

        if user_input in ["stop", "quit voice", "close voice"]:
            speak("Voice chat closed. Returning to main mode.")
            clear_audio_queue()
            break

        if user_input in ["exit assistant", "close assistant", "shutdown assistant"]:
            speak("Closing assistant. Goodbye.")
            running = False
            break

        print("Thinking...")
        ai_reply = ask_llama(user_input)

        save_history(user_input, ai_reply)
        speak(ai_reply)

        clear_audio_queue()


# -------------------------------------------------
# WAKE WORD LISTENER
# -------------------------------------------------

def wake_word_listener():
    global running, text_mode_active

    print("\nVoice activation is ON.")
    print("Say 'Jarvis' one time to start proper voice chat.")

    try:
        with sd.RawInputStream(
            samplerate=16000,
            blocksize=8000,
            dtype="int16",
            channels=1,
            callback=callback
        ):
            rec = vosk.KaldiRecognizer(model, 16000)

            while running:
                if text_mode_active:
                    clear_audio_queue()
                    time.sleep(0.2)
                    continue

                data = q.get()

                if rec.AcceptWaveform(data):
                    result = json.loads(rec.Result())
                    text = result.get("text", "").lower().strip()

                    if text:
                        print("Heard:", text)

                    if WAKE_WORD in text:
                        print("Wake word detected.")
                        voice_chat_mode()
                        rec = vosk.KaldiRecognizer(model, 16000)

    except Exception as e:
        print("Microphone error:", e)


# -------------------------------------------------
# QR CODE SCANNING FUNCTION
# -------------------------------------------------

def QR_scan(file_path):
    try:
        img = cv2.imread(str(file_path))

        if img is None:
            return "Image not found or cannot be opened."

        detector = cv2.QRCodeDetector()
        data, points, straight_qrcode = detector.detectAndDecode(img)

        if data:
            return "QR Code Data: " + data
        return "No QR code found in the image."

    except Exception as e:
        return "Error scanning QR code: " + str(e)


# -------------------------------------------------
# COUNT FILES FUNCTION
# -------------------------------------------------

def count_files(folder_path=UPLOAD_FOLDER):
    folder = Path(folder_path)
    folder.mkdir(parents=True, exist_ok=True)
    return sum(1 for item in folder.iterdir() if item.is_file())


# -------------------------------------------------
# UPLOAD FILES FUNCTION
# -------------------------------------------------

def upload_files(file_name):
    UPLOAD_FOLDER.mkdir(parents=True, exist_ok=True)
    file_path = UPLOAD_FOLDER / file_name

    if not file_path.exists():
        return {
            "status": "error",
            "message": "File not found. Put the file inside the Upload File folder and type the exact filename with extension.",
            "text": "",
            "file_type": "unknown",
            "path": file_path
        }

    extension = file_path.suffix.lower()

    if extension == ".pdf":
        try:
            reader = PdfReader(str(file_path))
            text = ""

            for page in reader.pages:
                page_text = page.extract_text()
                if page_text:
                    text += page_text + "\n"

            if not text.strip():
                return {
                    "status": "no_text",
                    "message": "PDF has no extractable text. It may be a scanned PDF image.",
                    "text": "",
                    "file_type": "pdf",
                    "path": file_path
                }

            return {
                "status": "success",
                "message": "PDF text extracted successfully.",
                "text": text,
                "file_type": "pdf",
                "path": file_path
            }

        except Exception as e:
            return {
                "status": "error",
                "message": "Error reading PDF: " + str(e),
                "text": "",
                "file_type": "pdf",
                "path": file_path
            }

    elif extension in [".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".webp"]:
        try:
            if TESSERACT_PATH is None or not Path(TESSERACT_PATH).exists():
                return {
                    "status": "error",
                    "message": "Tesseract OCR not found. Install Tesseract or place it inside JARVIS\\Tesseract-OCR.",
                    "text": "",
                    "file_type": "image",
                    "path": file_path
                }

            img = cv2.imread(str(file_path))

            if img is None:
                return {
                    "status": "error",
                    "message": "Image not found or cannot be opened.",
                    "text": "",
                    "file_type": "image",
                    "path": file_path
                }

            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            gray = cv2.medianBlur(gray, 3)
            thresh = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[1]

            text = pytesseract.image_to_string(thresh)

            if text.strip():
                save_path = UPLOAD_FOLDER / "extracted_image_text.txt"
                with open(save_path, "w", encoding="utf-8") as f:
                    f.write(text)

                return {
                    "status": "success",
                    "message": f"Text found in image and saved to {save_path}",
                    "text": text,
                    "file_type": "image",
                    "path": file_path
                }

            return {
                "status": "no_text",
                "message": "No readable text found in the image.",
                "text": "",
                "file_type": "image",
                "path": file_path
            }

        except Exception as e:
            return {
                "status": "error",
                "message": "Error reading image: " + str(e),
                "text": "",
                "file_type": "image",
                "path": file_path
            }

    elif extension in [".txt", ".py", ".csv", ".md", ".json", ".html", ".css", ".js", ".cpp", ".c", ".java"]:
        try:
            with open(file_path, "r", encoding="utf-8") as file:
                content = file.read()

            if not content.strip():
                return {
                    "status": "no_text",
                    "message": "Text file is empty.",
                    "text": "",
                    "file_type": "text",
                    "path": file_path
                }

            return {
                "status": "success",
                "message": "Text file read successfully.",
                "text": content,
                "file_type": "text",
                "path": file_path
            }

        except UnicodeDecodeError:
            return {
                "status": "error",
                "message": "This text file has unsupported encoding. Save it as UTF-8 and try again.",
                "text": "",
                "file_type": "text",
                "path": file_path
            }
        except Exception as e:
            return {
                "status": "error",
                "message": "Error reading text file: " + str(e),
                "text": "",
                "file_type": "text",
                "path": file_path
            }

    return {
        "status": "error",
        "message": "Unsupported file format.",
        "text": "",
        "file_type": "unknown",
        "path": file_path
    }


# -------------------------------------------------
# HANDLE FILE UPLOAD
# -------------------------------------------------

def handle_uploaded_file():
    speak("Please enter the filename to upload with extension.")
    file_name = input("Filename: ").strip()

    result = upload_files(file_name)

    print(result["message"])
    speak(result["message"])

    if result["status"] == "error":
        return

    if result["file_type"] == "image" and result["status"] == "no_text":
        user_choice = input("No text found. Type QR to scan QR code, or type your question about the image: ").strip()

        if user_choice.lower() == "qr":
            qr_result = QR_scan(result["path"])
            speak(qr_result)
        else:
            prompt = (
                "The user uploaded an image, but no readable text was found. "
                "You are using a text-only local model, so you cannot actually see the image. "
                "Tell the user that OCR found no text and ask them to describe the image or use a vision model.\n"
                "User question: " + user_choice
            )
            ai_reply = ask_llama(prompt)
            save_history(user_choice, ai_reply)
            speak(ai_reply)
        return

    if result["file_type"] == "pdf" and result["status"] == "no_text":
        question = input("No text found in PDF. What should I tell Llama about this PDF? ").strip()

        prompt = (
            "The user uploaded a PDF, but no extractable text was found. "
            "It may be a scanned PDF. Tell the user OCR is needed for scanned PDFs.\n"
            "User question: " + question
        )
        ai_reply = ask_llama(prompt)
        save_history(question, ai_reply)
        speak(ai_reply)
        return

    if result["text"].strip():
        print("\nExtracted text preview:")
        print(result["text"][:1000])

        question = input("\nWhat should I search/explain from this file? ").strip()

        prompt = f"""
You are given the content of a file.

File type: {result["file_type"]}

User question:
{question}

File content:
{result["text"]}

Now answer the user's question clearly.
"""

        ai_reply = ask_llama(prompt)
        save_history(question, ai_reply)
        speak(ai_reply)


# -------------------------------------------------
# TEXT MODE
# -------------------------------------------------

def text_mode():
    global text_mode_active
    text_mode_active = True

    speak("Text mode activated.")

    while running:
        user_input = input("\nYou: ").strip()
        lower_input = user_input.lower()

        if lower_input == "clear":
            UPLOAD_FOLDER.mkdir(parents=True, exist_ok=True)
            deleted = 0

            for item in UPLOAD_FOLDER.iterdir():
                if item.is_file():
                    item.unlink()
                    deleted += 1

            speak(f"Upload folder cleared. Deleted {deleted} files.")
            continue

        if lower_input == "count":
            num_files = count_files()
            speak(f"There are {num_files} files in the upload folder.")
            continue

        if lower_input in ["search", "file", "upload"]:
            handle_uploaded_file()
            continue

        if lower_input in ["bye", "quit", "stop", "exit"]:
            speak("Closing text mode.")
            text_mode_active = False
            clear_audio_queue()
            break

        print("Thinking...")
        ai_reply = ask_llama(user_input)
        save_history(user_input, ai_reply)
        speak(ai_reply)


# -------------------------------------------------
# HISTORY FUNCTION
# -------------------------------------------------

def save_history(user_input, ai_reply):
    HISTORY_FOLDER.mkdir(parents=True, exist_ok=True)

    # Java creates this file once when the app opens and passes it to Python.
    # This keeps mic-mode and text/backend messages in the same app-session history.
    session_file = os.environ.get("JARVIS_HISTORY_FILE", "").strip()

    if session_file:
        filename = Path(session_file)
        filename.parent.mkdir(parents=True, exist_ok=True)
    else:
        # Fallback for running voiceass.py directly without Java.
        timestamp = time.strftime("%Y-%m-%d_%H-%M-%S")
        filename = HISTORY_FOLDER / f"chat_{timestamp}.txt"

    current_time = time.strftime("%Y-%m-%d %H:%M:%S")

    with open(filename, "a", encoding="utf-8") as f:
        f.write("Time: " + current_time + "\n")
        f.write("User:\n" + str(user_input) + "\n\n")
        f.write("AI:\n" + str(ai_reply) + "\n")
        f.write("-" * 60 + "\n\n")


# -------------------------------------------------
# RUN TERMINAL ASSISTANT
# -------------------------------------------------

def run_terminal_assistant():
    global running

    print("HELLO SIR HOW MAY I HELP YOU TODAY?")

    voice_thread = threading.Thread(target=wake_word_listener, daemon=True)
    voice_thread.start()

    while running:
        typed = input("\nType hi/hello for text mode, or exit to close: ").lower().strip()

        if typed in ["hi", "hello"]:
            text_mode()

        elif typed in ["bye", "quit", "stop", "exit"]:
            speak("Thank you. Meet you soon.")
            running = False
            break

        else:
            print("Say 'Jarvis' one time for voice chat mode or type 'hi' for text mode.")


# -------------------------------------------------
# MAIN PROGRAM
# -------------------------------------------------

if __name__ == "__main__":
    speak("Testing voice before starting assistant.")
    run_terminal_assistant()
