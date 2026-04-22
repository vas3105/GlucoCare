# 🩺 GlucoCare – AI-Assisted Diabetes Management App

![Platform](https://img.shields.io/badge/Platform-Android-green)
![Language](https://img.shields.io/badge/Language-Java-blue)
![Architecture](https://img.shields.io/badge/Architecture-MVVM--Inspired-orange)
![Database](https://img.shields.io/badge/Database-Room%20%7C%20Firestore-yellow)
![OCR](https://img.shields.io/badge/OCR-Tesseract%20%7C%20ML%20Kit-red)
![Status](https://img.shields.io/badge/Status-Active-success)

GlucoCare is an intelligent Android-based application designed to simplify diabetes management through automated data capture, real-time monitoring, and AI-assisted insights.

---

## Overview

Managing diabetes requires continuous tracking of glucose levels and medication adherence. Traditional apps rely heavily on manual input, leading to inconsistent usage.

GlucoCare solves this by:
- Automating data entry using **OCR**
- Providing **real-time synchronization**
- Delivering **AI-driven insights and alerts**

---

## Key Features

### 📷 OCR-Based Data Logging
- Extracts glucose readings from:
  - Glucometer screens  
  - Medical reports  
- Uses **Tesseract OCR (primary)** + **ML Kit (fallback)**
- Fully **on-device processing**

### 📊 Smart Health Tracking
- Tracks glucose levels and medication logs  
- Visual dashboard for trends  

### ⏰ Medication Reminders
- Automated alerts for medication adherence  

### ☁️ Offline-First Sync
- **Room DB** for local storage  
- **Firestore** for cloud sync  

### 🔐 Authentication
- Firebase Authentication (login/signup)  
- Secure per-user data isolation  

### ⚠️ Emergency Alerts
- Detects missed logs / abnormal patterns  
- Triggers safety alerts  

---

## Architecture

- MVVM-inspired architecture  
- Fragment-based navigation  
- Repository pattern for data handling  
- Offline-first design (local → cloud sync)

---

## Tech Stack

**Mobile:**  
- Java (Android), XML  

**UI:**  
- Fragments, RecyclerView, CardView  
- Material Design  

**Backend & Data:**  
- Firebase Auth  
- Firestore  
- Room Database  

**OCR & AI:**  
- Tesseract OCR  
- Google ML Kit  

**Networking & Processing:**  
- OkHttp, Gson  
- ExecutorService  

---

## Workflow

1. Capture image (glucometer/report)  
2. OCR extracts glucose value  
3. Data stored locally (Room)  
4. Synced to Firestore  
5. Insights + reminders generated  

---
## Future Scope

- Wearable device integration  
- Advanced AI predictions  
- Doctor-patient connectivity  
- Cloud analytics  

---

## 🏁 Conclusion

GlucoCare leverages **OCR + AI + cloud technologies** to reduce manual effort, improve adherence, and enhance diabetes self-management.
