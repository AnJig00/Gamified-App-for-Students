**1\. Introduction**

**1.1. Project Overview:** This project is a mobile app designed to help students manage their studies and personal growth in a more engaging way. To keep students motivated, the app uses several game-like features. By completing personal goals and real-world activities, students can earn Points, unlock achievement Badges, and compete on staged Leaderboards. What makes this app different is its focus on the real world. Instead of encouraging more online chatting, its key goal is to provide students with a tool to practice and improve their face-to-face social skills.

**1.2. Scope**

*   **In-Scope:** The project will deliver a native Android application with the features outlined in this document, supported by a Python-based backend. Key functional areas include personal task management, a class timetable, a focus mode, a step counter, a Bluetooth-based social interaction module, and a comprehensive gamification system.
*   **Out-of-Scope:** This project will not include direct integration with university systems (e.g., Blackboard), real-time online chat functionality, or complex RPG mechanics like character classes or equipment.

**2\. User Personas**

To guide the design, we consider the following target user:

*   **Name:** Yade
*   **Bio:** A first-year university student feeling overwhelmed. Tasks from different classes and personal life are disorganized. Alex finds it hard to stay motivated for long study sessions and feels a bit socially isolated on a large campus.
*   **Goals:**
    *   Find a single place to organize all tasks.
    *   Build better study habits.
    *   Find a low-pressure way to meet new people.

**3\. Use Cases**

**3.1. Use Case Diagram:** The following diagram provides a high-level overview of the main interactions the "Student" actor can have with the application.

![](./images/Use Case Diagram.png)

**3.2. Master Use Case List:** This list provides a comprehensive overview of all planned functionalities for the application.

**Account Management**

*   UC-AM-01: Register for a new account
*   UC-AM-02: Log in with email and password
*   UC-AM-03: Log out
*   UC-AM-04: View user profile (showing username, level, points, badges)

**Productivity Tools**

*   UC-PD-01: Add a new personal task
*   UC-PD-02: Mark a task as complete
*   UC-PD-03: Add a new class to the weekly timetable
*   UC-PD-04: Receive a notification before a class starts
*   UC-PD-05: Start a focus session with a user-defined duration
*   UC-PD-06: Successfully complete a focus session to earn rewards
*   UC-PD-07: Fail or interrupt a focus session and receive a penalty

**Real-World Activities**

*   UC-RW-01: View daily step count
*   UC-RW-02: Enable or disable the social interaction feature
*   UC-RW-03: Mutually confirm a social interaction with another user via Bluetooth

**Gamification Engine**

*   UC-GM-01: Receive points after completing a rewarded action
*   UC-GM-02: Lose points after failing a challenge
*   UC-GM-03: Receive a notification upon leveling up
*   UC-GM-04: View the collection of all available badges
*   UC-GM-05: View the user's current rank within their league on the Leaderboard
*   UC-GM-06: Browse and purchase virtual items with in-app credits

**3.3. Detailed Use Cases & Sequence Diagrams (详细用例与顺序图)** The following sections describe the flow of key use cases, starting with fundamental actions and highlighting the key innovative feature with a sequence diagram.

**3.3.1. Fundamental Use Cases**

*   **Use Case: User Registration and Login**
    *   **Actor:** A new or returning Student.
    *   **Goal:** To create a new account or access an existing one.
    *   **Registration Flow:**
        1.  A new user opens the app and selects "Register."
        2.  The system asks for an email and a password.
        3.  The user provides the details and submits the form.
        4.  The system creates a new user account and automatically logs the user in.
    *   **Login Flow:**
        1.  A returning user opens the app and selects "Login."
        2.  The system asks for their registered email and password.
        3.  The user provides the credentials and submits.
        4.  The system verifies the credentials and grants the user access to the main application.

**3.3.2. Core Feature Use Cases**

*   **Use Case: Add a New Personal Task**
    *   **Goal:** To allow a logged-in user to add a new task to their To-Do list.
    *   **Flow:** The user navigates to the "To-Do List," taps "Add Task," fills in the details (title, due date), and saves. The new task then appears on their list.

![](./images/Add New Task.png)

*   **Use Case: Complete a Focus Mode Session**
    *   **Goal:** To help a logged-in user study without distraction and earn points.
    *   **Flow:** The user navigates to the "Focus Mode," sets a timer, and starts the session. If they complete the session without switching apps, they are awarded points. If they fail, they receive a small point penalty.

![](./images/Focus Mode.png)

**3.3.3. Key Innovative Use Case**

*   **Use Case: Confirm a Face-to-Face Social Interaction**
    *   **Actor:** Two students (Student A, Student B).
    *   **Precondition:** Both users are logged in, have the app open, and have enabled the social feature.
    *   **Main Flow:**
        1.  Student A and Student B meet and have a conversation in the real world.
        2.  Both students open the social feature in the app. The app uses Bluetooth to detect proximity.
        3.  Both students tap a "Confirm Interaction" button on their respective devices to mutually verify the interaction.
        4.  The system registers the confirmation and awards both students points.

![](./images/Confirm Social Interaction.png)