# Speech and the word book

## Speech

### Facts and rules

When an NPC wants to speak, it builds a list of **facts** about the moment: the event, its own traits and tiers, quirks, mood, the listener and its opinion of them, faction relations, its family, time of day, weather, season and place, and so on. The full list of facts and words is at the top of `basics.speech`.

Each line has **criteria** on those facts. The line whose criteria match the most facts wins (ties broken at random, weighted toward lines not used recently). Generic lines are the fallback; specific lines override them automatically. A hand-crafted character is just lines with the criterion `name = X`.

### Grammar

In outline (the exact file format is described at the top of `basics.speech`), lines contain **symbols** that expand into one of several alternatives, which can contain more symbols. Alternatives can carry the same criteria as lines (trait tiers, quirks, facts).

```
greet        -> [hello] [comment]
hello        -> Oh, hey [player]!          (manners: polite, lean)
             |  You.                       (manners: rude, strong)
             |  Hello.                     (neutral)
comment      -> [harvest_talk]             (if: own farm failed)
             |  Haven't had [food.a] in days.   (quirk: glutton)
harvest_talk -> Wheat didn't take. Next year, though!     (outlook: optimist, lean)
             |  Wheat's dead. Figures.                    (outlook: pessimist, strong)
             |  Wheat didn't take this year.              (neutral)
```

Word symbols come from the NPC's data: `[name]`, `[faction]`, `[enemy_faction]`, `[favorite_food]`, `[partner]`, `[home_biome]`, `[weather_word]`, `[kin_call]`, the words players taught (`[taught_food]`), and so on.

**Agreement.** Word symbols know their form (one item, plural or mass noun) and expand correctly:
- `[food.a]`: "a pork chop", "some blackberries", "some bread"
- `[food.the]`: "the pork chop", "the blackberries"
- `[food.it]`: "it" or "them"
- `[food.is]`: "is" or "are"
- `.cap` capitalizes: `[food.it.cap] [food.is] the best.`

**Repetition.** Each NPC remembers its recent lines and avoids them; recently used alternatives are weighted down.

### Longer lines

A remark on a topic is built from up to three **beats**: a reaction, a detail or reason, and a turn to the listener ("Rain again. Good for the crops, I suppose. You staying dry?"). The last two are optional pieces, written `[weather_detail?]`: each is said with the speaker's **chattiness** as its chance, and simply left out if nothing fits. Chattiness starts at 45%, rises with sociability (up to 25 points) and the chatterbox quirk (30), falls with the taciturn quirk (35), a bad mood (15) or a listener they dislike (20), rises a little for friends (10), and stays between 5% and 95%. So the same lines make a quiet NPC say one sentence and a chatty one three, and a remark is always one chat message.

The beats for the weather, food and home are in `topics.speech`; idle remarks and small talk with the player use them alongside their one-liners. Every beat is written to read on its own and after any other beat of its topic.

### Chats between NPCs

The NPC who starts a chat picks a **topic**, and the other answers on the same topic, with its own facts: its own tastes, its own home, its own opinion of whoever the news is about. Built-in topics (`speech/ChatTopics`): the weather, food (often, for gluttons), home (mostly away from it), and general chat, which holds the family and age lines. Other systems add topics from their setup: gossip adds fresh news and old news. A topic's lines are `chat_open_<topic>` and `chat_reply_<topic>`; a topic with nothing to say right now (no news to tell) is skipped, and general chat is always there to fall back on.

### Gossip

Things that happen become **news** (`gossip/`): couples dating, getting engaged, marrying or splitting up, births, deaths, killings, attacks out of nowhere (not enemies, not in the middle of a fight), and brawls. Everyone near where it happened knows it first-hand, and so do those involved and their families, wherever they are (it's kept in the world's register, `lifeblood_gossip`, not in each NPC). After that it travels by word of mouth:
- In NPC chats, the "news" topic passes on the biggest, newest piece the listener hasn't heard. Nosy NPCs bring news up three times as often, the taciturn less than half as often. Everyone remembers who told them ("I heard it from Tom").
- Nobody tells people news about themselves, and nobody spreads shameful news (an attack, a killing) about a friend.
- News is fresh for a real month (a twelfth of an in-game year). After that, only big news (weddings, births, deaths, killings) comes up again, now and then, as "remember when" (more often for the sentimental).
- **Reputation.** Hearing that someone attacked or killed a person lowers the hearer's opinion of them by a quarter of what seeing it would: "heard they hurt someone", "heard they killed someone", or, if the victim was their friend or family, the same memory a witness would get, at a quarter of the weight (an attack on a friend counts double). So treating one person badly costs you with the whole town, the player included.
- **Liars** dress stories up half the time they pass one on ("knocked him out cold with one punch!"). An exaggerated story stays exaggerated as it travels, and counts half again as much against whoever it's about. Lines know it (`exaggerated`), and some listeners say it sounds made up.
- **The player** can ask anyone "Heard any news?" and hear the freshest piece they haven't heard yet.

Lines are in `gossip.speech`, with the facts `news`, `exaggerated`, `firsthand`, `subject_opinion` and `other_opinion`, and the words `subject`, `other`, `baby` and `source`.

### Sounding their age

Young NPCs talk like their age, all the time. Growing up is split into stages: **baby** (0–2), **child** (3–9), **young teen** (10–15) and **teen** (16–17), on the same 18-year scale as growing up (so it follows `yearsToGrowUp`). The `age` fact holds the stage, and `listener_age` the listener's.

A symbol can have a version per stage, written `greet.child` or `smalltalk.teen`, in `ages.speech`. A young speaker uses its stage's version instead of the plain symbol, and only falls back to the plain one if nothing in its version fits. Babies only babble: any symbol without a `.baby` version comes out of `babble`, and babies never ask for words. Kids and teens have their own versions of greetings, small talk, opening and closing a conversation, questions about themselves, following, gifts, birthdays, idle thoughts, chats with other NPCs, running away and grief, with lines for the axes and quirks and for talking to parents, grandparents and siblings (`[kin_call]` is Mom, Dad, Grandma or Grandpa).

Grown-ups talk to the young differently too: in chats between NPCs, parents fuss over their children by stage (cooing at babies, chores and curfews for young teens, the future for older teens), and other adults talk down to little kids.

### Speech style

A surface layer rolled per NPC, independent of personality, so two NPCs with the same traits still sound different. It has:
- **words:** what they call people (`address`: "friend", "mate", "sir"), a catchphrase said now and then, and a filler they tack on, picked from the `style_address`, `style_catchphrase` and `style_filler` symbols in `basics.speech`;
- **habits:** dropped g's ("goin'") and trailing off ("...").

The pools lean on the NPC's personality where it fits (a proper speaker says "sir"); otherwise the pick is random. Because style supplies fragments rather than whole lines, it adds to the writing instead of multiplying it.

The habits (dropped g's, trailing off) are for life, but the words grow up with the NPC. Address, catchphrase and filler are picked for the NPC's age stage from the stage versions of the style symbols in `ages.speech` (a little kid says "Pinky promise!" and calls you "buddy"; a young teen says "Whatever."), and picked again each time it reaches a new stage. Babies have none. About a quarter of teenagers keep their catchphrase into adulthood.

### Chat

Speech appears in chat as `<Name> line` within a hearing radius (about 24 blocks), for barks, direct messages to the player and conversations between NPCs. Each NPC has a bark cooldown (chatterbox shorter, taciturn longer), and there is a global rate limit so a 30-NPC battle doesn't flood chat.

## Word book

NPCs occasionally ask the player for a new word: sometimes a chat ("How are you?") turns into "What would you call a food you'd eat for breakfast?", counting as one of the five chats a minute. Friendlier, curious and chatty NPCs ask more often. The player can answer or decline. The text box says what kind of word fits, with an example, and shows the words that come before it ("a(n) ___", "Welcome to ___", "I love ___"), and leading "a", "an", "the" or "some" are dropped from what's typed, so lines never say "a a squidbug".

After learning a word, the NPC asks whether it's **just for them** or **something anyone could say**:
- **Just for them:** a personal word, like their own catchphrase or their nickname for you. It never spreads.
- **Anyone:** a general word. It spreads through conversations between NPCs who are at least friendly, so it travels through a town but not to strangers far away.

Each word has a **category** that says exactly what it is, which keeps sentences coherent:

| Category | Forms asked for | Example use |
|---|---|---|
| Food | one item / plural / mass, plus the other form if needed | "Haven't had [food.a] in days." |
| Animal | singular and plural | "Saw [animal.a] by the river." |
| Thing | singular and plural | "Lost my [thing] again." |
| Place | name | "Ever been to [place]?" |
| Surprised | text | "[exclaim]! Did you see that?" |
| Angry, happy, sad, scared | text | said when they're mad, delighted, grieving or frightened |
| Greeting | text | Used as their hello |
| Farewell | text | Used as their goodbye |
| Nickname for you | text | Used instead of your name |
| Describing word | text | "That's [describe]." |
| Activity | as a doing-word phrase ("rock skipping") | "Been [activity] lately?" |

- **How often.** An NPC that likes you at least a little (20+) asks at most once a day, about a quarter of the times you talk to it (twice as often if curious or a chatterbox, half if taciturn), until it knows 15 words. With `/lb debug limits off` it asks every time.
- **Spreading.** Each friendly NPC chat (both at 20+) has a 30% chance to pass on a general word the other doesn't know. Everyone remembers who they heard it from.
- **In speech,** each NPC uses one known word per category in a line, as `[taught_food]`, `[taught_greeting]` and so on. A nickname you taught replaces your name about half the time.
- **No fading.** Words stay until the player deletes them.
- **No editing.** A word can only be deleted, never changed.
- **The word book screen** lists every taught word: category, personal or general, who you taught it to, and how many NPCs know it. Deleting a word removes it from everyone. The **Word Book** item (a book and a feather) opens it; `/lb words list` and `/lb words delete <id>` do the same by command.
- **Per NPC**, a dialogue option ("What words do you know?") lists that NPC's six newest words and where they picked each one up; the word book has the rest.
