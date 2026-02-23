# COEN-ELEC390 Project - QuietZone

## Overview

QuietZone is a real-time noise monitoring system designed to help users find quiet locations in various environments. Built with distributed sound sensors and IoT communication, it enables users to view live noise levels across monitored areas through an intuitive mobile interface, while providing administrators with comprehensive sensor management capabilities.

## Problem Statement

Finding quiet spaces in busy environments like libraries, study areas, or campuses can be challenging without real-time information about noise levels. Existing solutions often lack accessibility, real-time updates, or user-friendly interfaces. QuietZone aims to provide an accessible, scalable noise monitoring system that helps users make informed decisions about where to find peaceful environments for work, study, or relaxation.

## Features

### Core Functionality

**Real-Time Noise Monitoring**

- Sound sensor-based decibel (dB) level measurement with continuous monitoring
- Live noise data display for multiple monitored areas
- Noise level categorization (Low, Medium, High) for easy interpretation

**Admin Sensor Management**

- Sensor registration and assignment to monitored areas
- Real-time sensor data monitoring and validation
- Inactivity detection to identify malfunctioning sensors

**User-Friendly Mobile Interface**

- Android app displaying current noise levels for each area
- Automatic data refresh (≤5 seconds) for real-time updates
- Noise categorization to help users quickly identify quiet zones

## Technology Stack

**Embedded Hardware:** ESP32 (Arduino Framework)  
**Sensors:** Sound Sensors (dB measurement), LED Indicators  
**Communication:** WiFi for wireless data transmission  
**Central Hub:** Raspberry Pi (Linux-based)  
**Database:** Firebase (Cloud database)  
**Mobile Platform:** Android (API Level 24+)  
**Development Tools:** PlatformIO, Android Studio, Gradle  
**Version Control:** GitHub

## Development Process

This project follows an Agile Scrum methodology with iterative development cycles called sprints.

**Sprint 1 Goal:** Configure sound sensor and Raspberry Pi, establish basic communication with database, implement basic mobile app skeleton, and implement initial Admin functionality

**Sprint 2 Goal:** Implement live noise display for users, noise categorization system, and inactivity detection for sensors

**Version Control & Collaboration:** Managed using GitHub with structured repositories and sprint-based development

## Team Members

| Name       | Student ID | Role        |
| ---------- | ---------- | ----------- |
| TBD        | TBD        | Team Member |
| TBD        | TBD        | Team Member |
| TBD        | TBD        | Team Member |
| TBD        | TBD        | Team Member |
| Tonny Zhao | 40283194   | Team Member |

## Project Repository

**GitHub Repository:** [COEN-ELEC290-QuietZone/QuietZone](https://github.com/COEN-ELEC290-QuietZone/QuietZone)

## License

This project is developed as part of the COEN-ELEC390 course and is intended for educational purposes.
