
import pandas as pd
import torch

from datasets import Dataset
from transformers import (
    AutoTokenizer,
    AutoModelForSequenceClassification,
    TrainingArguments,
    Trainer
)

from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, precision_recall_fscore_support


# 1. Load dataset
data = pd.read_csv("/content/ElderGuard/data/fraud_dataset.csv")

print("Total examples:", len(data))


# 2. Split dataset
train_data, test_data = train_test_split(
    data,
    test_size=0.2,
    random_state=42,
    stratify=data["label"]
)

train_dataset = Dataset.from_pandas(train_data)
test_dataset = Dataset.from_pandas(test_data)


# 3. Load DistilBERT
model_name = "distilbert-base-uncased"

tokenizer = AutoTokenizer.from_pretrained(model_name)


# 4. Tokenize
def tokenize_function(examples):
    return tokenizer(
        examples["text"],
        padding="max_length",
        truncation=True,
        max_length=128
    )


train_dataset = train_dataset.map(
    tokenize_function,
    batched=True
)

test_dataset = test_dataset.map(
    tokenize_function,
    batched=True
)


# 5. Load pretrained model
model = AutoModelForSequenceClassification.from_pretrained(
    model_name,
    num_labels=2
)


# 6. Evaluation metrics
def compute_metrics(eval_pred):

    predictions, labels = eval_pred

    predictions = predictions.argmax(axis=-1)

    precision, recall, f1, _ = precision_recall_fscore_support(
        labels,
        predictions,
        average="binary",
        zero_division=0
    )

    accuracy = accuracy_score(labels, predictions)

    return {
        "accuracy": accuracy,
        "precision": precision,
        "recall": recall,
        "f1": f1
    }


# 7. Check GPU
print("\nCUDA available:", torch.cuda.is_available())

if torch.cuda.is_available():
    print("Training GPU:", torch.cuda.get_device_name(0))


# 8. Training settings
training_args = TrainingArguments(

    output_dir="/content/ElderGuard/model/elderguard_distilbert",

    num_train_epochs=5,

    per_device_train_batch_size=4,

    per_device_eval_batch_size=4,

    eval_strategy="epoch",

    save_strategy="epoch",

    logging_strategy="epoch",

    load_best_model_at_end=True,

    metric_for_best_model="f1",

    greater_is_better=True,

    fp16=torch.cuda.is_available(),

    report_to="none"
)


# 9. Trainer
trainer = Trainer(

    model=model,

    args=training_args,

    train_dataset=train_dataset,

    eval_dataset=test_dataset,

    processing_class=tokenizer,

    compute_metrics=compute_metrics
)


# 10. Train
print("\nStarting ElderGuard training...\n")

trainer.train()


# 11. Evaluate
results = trainer.evaluate()

print("\nFinal Evaluation:")
print(results)


# 12. Save model
trainer.save_model(
    "/content/ElderGuard/model/elderguard_distilbert"
)

tokenizer.save_pretrained(
    "/content/ElderGuard/model/elderguard_distilbert"
)

print("\nModel saved successfully!")
