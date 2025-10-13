# **Research Document**

  
## Project Title: Gamified App For Student

## Author: Shuai Jiang

## Date: 12/10/2025

# 1  Introduction
## 1.1  Problem Statement
Higher education students today face a unique set of challenges. They often struggle with disorganized tasks from various sources, difficulty in maintaining motivation for self-directed study, and a sense of social isolation on a large campus. Balancing these academic pressures with personal well-being is a challenge.

## 1.2  Project Vision & Goals
This project is a mobile app designed to help students manage their studies and personal growth in a more engaging way. To keep students motivated, the app uses several game-like features. By completing personal goals and real-world activities, students can earn Points, unlock achievement Badges, and compete on staged Leaderboards. What makes this app different is its focus on the real world. Instead of encouraging more online chatting, its key goal is to provide students with a tool to practice and improve their face-to-face social skills.

# 2  Market and Competitive Analysis
## 2.1  Analysis: Habitica
### 2.1.1  Core Concept
After using it for a week, I found that Habitica is an innovative productivity tool. My understanding is that it changes a user's real-life tasks (including habits, daily to-do, and normal tasks) into a complete role-playing game (RPG). I saw that users get experience and gold by completing tasks in real life, which upgrades their virtual character in the game. I believe this method makes the process of completing boring tasks full of fun.

### 2.1.2  Gamification Mechanics
Some important features I identified are: Experience Points (XP) & Levels; Gold & Reward Shop; Health Points (Health) & Punishment; Character Role-Playing Elements: Equipment, Pets, and Mounts; and a Social System where users can form "Guilds".

### 2.1.3 Something can learn from this app

A. Valuable Concepts I Can Borrow

**Punishment Mechanism:** I think Habitica's "lose Health" punishment is very effective because it makes the user's promised tasks feel more important. For my own app, I can learn from this idea and use a more gentle consequence method. For example, if a user fails their "Focus Mode" challenge, they might lose a small number of points. I feel this is stronger than just getting "no reward."

**Internal Economy System:** I also found that Habitica's Gold and Shop system gives a second value to the user's effort, besides just upgrading. I believe I can learn from this point. In my app, I can let students use their earned points not only to level up, but also to exchange for personalized rewards (like new app themes or avatar looks) in a simple "shop". In my opinion, this will make earning points have more purpose.

B. Elements I Should Avoid

**Overly Complex RPG System:** For me, this is the most important point. I found that Habitica’s classes, equipment, and pet systems are very attractive to game players, but I think they might distract students' attention. My opinion is that users may spend a lot of their study time to research these game systems, which is opposite to my app's main goal of "improving study efficiency." For my project, I want to keep it simple, to make sure the gamification is for motivation, not the final goal itself.

**Unique UI Style:** I can accept Habitica's pixel art style, but I agree it is not for everyone. For an app that I want to build to help students focus and grow, I believe a more modern, simple, and calm interface is a better strategic choice.

C. Concepts I Can Adapt

**Changing "Guilds" into Offline "Study Groups":** I saw that Habitica's "Guilds" is a pure online social function. I think I can learn from its "team work" idea and adapt it to serve my "offline social" core goal. For example, in my app, users could form "Study Groups." When group members use the Bluetooth function to confirm they had an offline meeting, the whole group would get extra rewards. I think this will effectively motivate real study communication.

## 2.2 Comparative Analysis of Other Apps

This table summarizes the key insights learned from other relevant applications.

| App Name | Relevant Feature in Our Project | Key Gamification Method | Strengths to Borrow | Weaknesses / Differences |
| --- | --- | --- | --- | --- |
| Forest | Focus Mode | Successfully focusing grows a tree; failing kills the tree. The accumulated trees form a visual forest. | The "group focus" concept is a perfect inspiration for our offline "Study Groups". | Forest is almost entirely a focus timer. However, the application we are developing is a multifunctional student assistant app. |
| Headspace | UI/UX Design & Mental Well-being Module | It primarily uses daily streaks and a calm, encouraging UI/UX to build a habit. | The simple, calm and intuitive interface is deeply loved by users, and it also has a simple operation logic. | Headspace is about passive content consumption (listening). Our app's core is to encourage active user behaviors (completing tasks, socializing). |
| Duolingo | Gamification Engine (Leaderboards & Economy) | Its core loop is driven by a highly competitive weekly league system, daily streaks, and virtual currency (gems) used to purchase items such as "Streak Freeze". | The league-based leaderboard is the perfect model for our staged leaderboard system. The "Streak Freeze" is an excellent idea for our in-app "Credits" store. | The learning path is highly structured, which may make people feel monotonous and repetitive, and a bit boring. Our application needs to become a flexible toolbox. Its notification strategy might be overly aggressive. |

## 2.3 Project's Position

After analyzing these successful applications, we can find that they are usually designed as universal tools for a broad audience. They lack specific attention to the lives of students.

"Gamified Student Companion" precisely targets this gap. Its unique value is based on two core principles:

**Specifically for students**: The functions of this application are tailored to the daily schedules and challenges faced by students.

**Encouraging offline social interaction**: The key feature of this project lies in its innovative Bluetooth-based function, which rewards face-to-face interactions.

# 3  Technology Investigation

Here is a brief introduction of the specific tools I chose and the reasons for their selection:

**Architecture (Back-end + Front-end):** I chose this approach because it is flexible and it is also the method currently adopted by most companies. This means that once I write the core logic code in the back-end, it can be used to drive iOS applications or websites without me having to rewrite all the content.

**Back-end (Using Python and Django):** I chose to develop the back-end using Python because it is my most familiar language and it is known for its speed in development. I specifically chose the Django framework because its built-in features, such as the user login system and management pages, can save me a lot of time on basic work. This way, I can focus on the interesting and unique parts of the application, such as the gamification logic.

**Front-end (Using Kotlin and Android Studio):** For this mobile application itself, I will use the Kotlin language to build a native Android application. Kotlin is the modern official language for the Android system, and by focusing on a single platform, I can ensure that the user experience is as smooth and refined as possible. I will use Android Studio as the standard integrated tool for this project.

**Database (SQLite at the front, PostgreSQL at the back):** I started with SQLite because it is the default database for Django and requires no setup, which is ideal for quickly starting and running the project. Of course, PostgreSQL is a more professional choice for actual applications, so I plan to migrate it to it in the later stages of the project.

**API and Testing (DRF and Postman):** To enable communication between the back-end and front-end, I will use the Django REST framework (DRF) to create a simple REST API. At the same time, I will use tools like Postman to test the API during its construction. This way, I can ensure that the back-end logic can run independently before connecting it to the mobile application.

**Version Control (Git and GitHub):** I will use Git and GitHub to save and manage all my code and documents. The specific operations will be carried out as needed.

# 4  Conclusion

This research document confirms the existence of market opportunities for gamified applications targeting student groups. Through the analysis of existing applications, it provides a solid foundation for the adoption and adaptation of mechanisms and functions that have been proven effective. Additionally, the selected technology stack offers strong support for the subsequent development of this app.

# Reference

\[1\] Habitica. [https://habitica.com/](https://habitica.com/)

\[2\] Forest. [https://www.forestapp.cc/](https://www.forestapp.cc/)

\[3\] HeadSpace. [https://www.headspace.com/](https://www.headspace.com/)

\[4\] Duolingo. [https://www.duolingo.com/](https://www.duolingo.com/)