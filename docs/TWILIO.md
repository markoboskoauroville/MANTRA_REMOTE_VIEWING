# MANIFEST: Twilio

Marko's Twilio account (Full, paid by credit). The credentials live only in the file Marko hands over
(`TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`); never write them into a repo, a log, a commit or any other service.

## What the account holds (1.11.2026)

- No phone numbers of its own. `+14155238886` is Twilio's shared WhatsApp sandbox: messages only, **no voice calls**.
- Voice calls to Croatia (HR) switched on 1.11.2026 for the find-my-phone call and **kept on deliberately** (Marko's
  decision): he lives in Croatia and will need it again. Only low-risk numbers (ordinary mobiles and landlines);
  premium-rate and high-risk toll-fraud numbers stay off. The account is prepaid, so its balance is the most a thief
  with the token could spend; the real protection is keeping the auth token secret. Do not offer to switch it off again.
- Balance 18.84 USD after that call (was 19.99).

## Rule 1: a number is paid by the month, in advance, and releasing it throws the month away

- Buying a number charges the whole first month at once (US local about 1.15 USD).
- **Releasing a number is not cancelling a subscription.** The number goes back to Twilio's pool at once, the paid
  rest of the month is lost, there is no refund, and the same number cannot be bought back.
- So: **keep a bought number until shortly before its next monthly charge**, then release it. Write the purchase date
  and the release-by date here.
- Before any release, **tell Marko what is left of the paid month and ask**: "release now and lose N days, or keep it
  until <date>?" A "release it" spoken before he knows this is not an informed yes.

## Rule 2: say the cost and what can't be undone before spending

- Before buying, say the price, that it repeats monthly, and that releasing early refunds nothing.
- Before changing account settings (dialing permissions), say what changes and offer to change it back afterwards.

## What happened (1.11.2026): find my phone

Marko lost his phone and asked to be called through Twilio. The account had no voice number, so with his yes:
Croatia calls switched on (`voice.twilio.com/v1/DialingPermissions/BulkCountryUpdates`, the request needs all three
fields `low_risk_numbers_enabled`, `high_risk_special_numbers_enabled`, `high_risk_tollfraud_numbers_enabled`), US number
+1 425 493 4679 bought, called repeatedly (60 s ring, a spoken message when answered). He found the phone and said
"release the number"; it was released minutes after purchase. **Mistake:** he was not told that the paid month
(to about 1.12.2026) would be lost; he lost about 1.15 USD for a few minutes of use. Rule 1 exists because of this.

## Recipe: call Marko until he answers

1. `GET .../IncomingPhoneNumbers.json`: a voice-capable number? If none, Rule 2, then buy one
   (`AvailablePhoneNumbers/US/Local.json?VoiceEnabled=true`, `POST IncomingPhoneNumbers.json`).
2. Check `voice.twilio.com/v1/DialingPermissions/Countries/HR`; switch on low-risk numbers if off.
3. `POST Calls.json` with `To`, `From`, `Timeout=60`, `Twiml=<Response><Say loop="3">…</Say></Response>`; poll the call,
   call again until the status is `completed` (answered; voicemail also counts as completed).
4. Afterwards: end any ringing calls, then Rule 1 (don't release at once, ask). Croatia calls stay on.
