# MultiTetris — Firebase Spark (Free Tier) Setup Guide

Follow these steps to connect **MultiTetris** to your own free Firebase project for Anonymous Authentication, Friend Codes, Live Presence, and Automatic Matchmaking.

> **Important:** `app/google-services.json` is listed in `.gitignore`. Never commit your Firebase configuration file to GitHub.

---

## Step 1: Create a Free Firebase Project (Spark Plan)

1. Go to the [Firebase Console](https://console.firebase.google.com/).
2. Click **Add project** (or **Create a project**) and enter a name such as `MultiTetris`.
3. You can disable Google Analytics (not required for Stage 2) and click **Create project**.
4. Confirm your project is on the **Spark (Free)** plan (default).

---

## Step 2: Register the Android App

1. In your Firebase project overview, click the **Android** icon (`+ Add app`).
2. Enter the Android package name:
   ```text
   com.multitetris
   ```
3. Optional: add an app nickname (`MultiTetris`).
4. Click **Register app**, then download **`google-services.json`**.
5. Place the file at:
   ```text
   app/google-services.json
   ```
6. Rebuild the app (`.\gradlew.bat assembleDebug` or sync in Android Studio). The `com.google.gms.google-services` plugin automatically activates once `app/google-services.json` is present.

---

## Step 3: Enable Anonymous Authentication

1. In the left sidebar, open **Build → Authentication**.
2. Click **Get started**.
3. Go to the **Sign-in method** tab.
4. Select **Anonymous**, toggle **Enable** on, and click **Save**.

---

## Step 4: Create the Realtime Database

1. In the left sidebar, open **Build → Realtime Database**.
2. Click **Create Database**.
3. Choose your nearest database region (e.g., `us-central1` or `europe-west1`).
4. Start in **Locked mode** (we will immediately replace the rules in Step 5).

---

## Step 5: Paste the Realtime Database Security Rules

Open the **Rules** tab of your Realtime Database, paste the exact JSON below, and click **Publish**:

```json
{
  "rules": {
    ".read": false,
    ".write": false,

    "users": {
      "$uid": {
        ".read": "auth != null && (auth.uid === $uid || root.child('userFriends').child(auth.uid).child($uid).exists())",
        ".write": "auth != null && auth.uid === $uid",
        ".validate": "newData.hasChildren(['friendCode', 'nickname', 'selectedSkinId'])"
      }
    },

    "friendCodes": {
      "$code": {
        ".read": "auth != null",
        ".write": "auth != null && (!data.exists() || data.val() === auth.uid)",
        ".validate": "newData.isString() && newData.val() === auth.uid"
      }
    },

    "userFriends": {
      "$uid": {
        ".read": "auth != null && auth.uid === $uid",
        ".write": "auth != null && auth.uid === $uid"
      }
    },

    "presence": {
      "$uid": {
        ".read": "auth != null && (auth.uid === $uid || root.child('userFriends').child(auth.uid).child($uid).exists())",
        ".write": "auth != null && auth.uid === $uid",
        ".validate": "newData.hasChildren(['status', 'timestamp'])"
      }
    },

    "invites": {
      "$uid": {
        ".read": "auth != null && auth.uid === $uid",
        ".write": "auth != null && (auth.uid === $uid || (newData.exists() && newData.child('hostUid').val() === auth.uid && root.child('userFriends').child(auth.uid).child($uid).exists()))"
      }
    },

    "matches": {
      "$matchId": {
        ".read": "auth != null && (data.child('hostUid').val() === auth.uid || data.child('guestUid').val() === auth.uid)",
        ".write": "auth != null && ((!data.exists() && newData.child('hostUid').val() === auth.uid) || data.child('hostUid').val() === auth.uid || data.child('guestUid').val() === auth.uid)"
      }
    }
  }
}
```

### How These Rules Protect Players
- **`/users/$uid` & `/presence/$uid`:** Players can only write their own profile and presence nodes, and can only read their own node or nodes belonging to UIDs in their `/userFriends/{myUid}` list.
- **`/friendCodes/$code`:** Authenticated players can look up an exact `MT-XXXXX` code without listing the entire `/friendCodes` root, and cannot overwrite another player's claimed code.
- **`/invites/$uid` & `/matches/$matchId`:** Only saved friends can deliver match invites, and only the host or guest of a match can read or update its signaling room node.
