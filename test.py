
import torch

from transformers import (
    AutoTokenizer,
    AutoModelForSequenceClassification
)


# Load trained ElderGuard model
model_path = "/content/ElderGuard/model/elderguard_distilbert"

tokenizer = AutoTokenizer.from_pretrained(model_path)

model = AutoModelForSequenceClassification.from_pretrained(model_path)


# Use GPU if available
device = torch.device(
    "cuda" if torch.cuda.is_available() else "cpu"
)

model.to(device)

print("Device:", device)

if torch.cuda.is_available():
    print("GPU:", torch.cuda.get_device_name(0))


# Prediction function
def predict(text):

    inputs = tokenizer(
        text,
        return_tensors="pt",
        truncation=True,
        padding=True,
        max_length=128
    )

    inputs = {
        key: value.to(device)
        for key, value in inputs.items()
    }

    with torch.no_grad():

        outputs = model(**inputs)

        probabilities = torch.softmax(
            outputs.logits,
            dim=1
        )

    prediction = torch.argmax(
        probabilities,
        dim=1
    ).item()

    confidence = probabilities[0][prediction].item() * 100

    if prediction == 1:
        print("\nMessage:", text)
        print("Prediction: FRAUD 🚨")
        print(f"Confidence: {confidence:.2f}%")

    else:
        print("\nMessage:", text)
        print("Prediction: SAFE ✅")
        print(f"Confidence: {confidence:.2f}%")


# Test messages

predict(
    "This is CBI. Your Aadhaar is involved in an illegal case. Transfer money immediately."
)

predict(
    "Your electricity bill is due tomorrow."
)

predict(
    "You are under digital arrest. Do not disconnect this call."
)

predict(
    "Your order has been shipped and will arrive tomorrow."
)
