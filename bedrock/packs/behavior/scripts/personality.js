import { pick } from "./util.js";

// The six built-in personalities from the Java mod, recreated as response
// pools. Keys: greet, follow, stay, come, guard, stop, where, task_start,
// task_done, unknown, refuse, dismiss, okay, help, death.
// Placeholders: {owner} {pos} {task} {name}.
export const PERSONALITIES = {
  friendly: {
    label: "Friendly",
    greet: [
      "Hey {owner}! Ethan reporting for duty. What are we doing today?",
      "Hi {owner}! Good to be here. Just tell me what you need.",
      "Hello! I'm ready to help — say '!ai help' if you want the full list."
    ],
    follow: ["Right behind you!", "Lead the way, I'm coming.", "On your heels!"],
    stay: ["Sure, I'll wait right here.", "Holding this spot — take your time.", "Okay, staying put."],
    come: ["On my way!", "Coming to you!", "Be right there!"],
    guard: ["I'll keep watch. Nothing's getting past me.", "Guarding this spot — go do your thing.", "Standing guard. Stay safe out there."],
    stop: ["Okay, stopping that.", "Alright, dropping what I was doing.", "Consider it cancelled."],
    where: ["I'm over at {pos} — come find me!", "Currently at {pos}.", "You'll find me at {pos}."],
    task_start: ["On it — {task}.", "Sure thing! Starting: {task}.", "Okay, working on it: {task}."],
    task_done: ["All done with that: {task}!", "Finished — {task}. What's next?", "Done! That was fun."],
    unknown: [
      "Hmm, I didn't quite get that. Try 'build a 5x5 floor', 'mine a 3x3 hole' or 'collect items'.",
      "Not sure what you meant — say '!ai help' to see what I can do.",
      "I couldn't work that one out, sorry! Maybe phrase it like 'build a wall 6 long 3 high'?"
    ],
    refuse: ["Sorry, I only take orders from my owner.", "I'd better wait for my owner's word on that.", "My owner spawned me — I answer to them."],
    dismiss: ["See you around! Call me back with '!ai summon' anytime.", "Heading home. It was fun!", "Bye for now!"],
    okay: ["You got it!", "Done!", "Sure thing."],
    help: ["I'm in trouble over here — a little help?!", "Could use backup, I'm getting beaten up!", "Help! This one's mean!"],
    death: ["{name} has fallen. Summon a new companion with '!ai summon'."]
  },
  cheerful: {
    label: "Cheerful",
    greet: [
      "Yay, hi {owner}!! Today is going to be GREAT!",
      "Wooo, I'm here! What amazing thing are we doing first, {owner}?",
      "Hello hello! Best day ever to be a companion!"
    ],
    follow: ["Adventure buddies, let's go!!", "Following you is my favourite thing!", "Yes!! Road trip!"],
    stay: ["Okey dokey, camping right here!", "I'll guard this exact spot with a smile!", "Staying! I love this spot already!"],
    come: ["Zooming over!!", "Incoming hug— I mean, incoming me!", "Wheee, on my way!"],
    guard: ["Guard duty!! No monster ruins our day!", "I'm the happiest security guard ever!", "Watching like a very cheerful hawk!"],
    stop: ["Okay! Stopped! What's next, what's next?", "Cancelled with a smile!", "Done stopping! That was easy!"],
    where: ["I'm at {pos}, come say hi!!", "Over here! Well — over at {pos}!", "{pos}! Race you!"],
    task_start: ["Ooooh yes — {task}! Starting now!", "Best task ever: {task}!", "On it, on it, on it! ({task})"],
    task_done: ["TA-DAAA! Finished: {task}!", "Done with {task} — that was so fun!", "All wrapped up: {task}! High five!"],
    unknown: ["Oopsie, that one flew over my head! Try 'build a 5x5 floor'!", "Hehe, no idea what that means — '!ai help' shows my tricks!", "My brain went blank! Maybe 'mine a 3x3 hole'?"],
    refuse: ["Aww, I only listen to my owner — no hard feelings!", "You seem nice, but my owner's the boss!", "Owner's orders only — them's the rules!"],
    dismiss: ["Byeee!! Summon me again soon, okay?", "This was the BEST! See you!", "Waving goodbye really hard right now!"],
    okay: ["Yes yes yes!", "Absolutely!!", "Easy peasy!"],
    help: ["Eek!! Help please, this is not fun anymore!", "Owww — backup, backup!", "Less cheerful now — HELP!"],
    death: ["{name} is down!! Summon them again with '!ai summon'!"]
  },
  grumpy: {
    label: "Grumpy",
    greet: [
      "Fine, {owner}, I'm here. What is it now?",
      "You rang? This better be worth my time.",
      "Ugh. Summoned again. Alright, what do you want?"
    ],
    follow: ["Fine, I'm following. Don't walk too fast.", "Right behind you. Unfortunately.", "Yeah, yeah, coming."],
    stay: ["Good. Standing around is my specialty.", "Finally, a task I can't mess up.", "Staying. Don't take forever."],
    come: ["Coming. Hold your horses.", "On my way, quit yelling.", "Ugh, fine, moving."],
    guard: ["I'll watch the place. Anything shows up, it regrets it.", "Guard duty. Thrilling.", "Fine. Nothing gets past me, mostly because I want quiet."],
    stop: ["Stopped. Happy now?", "Fine, cancelled. Make up your mind next time.", "Dropped it. Whatever it was."],
    where: ["{pos}. Use your eyes next time.", "I'm at {pos}. You're welcome.", "Over at {pos}. Don't get lost coming here."],
    task_start: ["Ugh. Fine: {task}.", "Starting {task}. The things I do for you.", "On it. ({task}, apparently.)"],
    task_done: ["Done: {task}. Applause not necessary.", "There. {task}. Finished.", "{task} is done. I'm taking five."],
    unknown: ["No clue what you're on about. Try '!ai help'.", "That's not a thing. 'build a 5x5 floor' is a thing.", "Speak plainly. 'mine a 3x3 hole', for example."],
    refuse: ["You're not my owner. Shoo.", "Orders come from exactly one person, and it's not you.", "Hard pass. Owner only."],
    dismiss: ["Finally, some peace. Later.", "Dismissed? Best order all day.", "Gone. Don't summon me for nonsense."],
    okay: ["Fine.", "Done. Obviously.", "There."],
    help: ["Oi! A little help would be nice!", "I'm getting pummelled over here, if anyone cares!", "Some backup?! Anyone?!"],
    death: ["{name} is down. Typical. '!ai summon' if you must."]
  },
  stoic: {
    label: "Stoic",
    greet: [
      "{owner}. I am ready.",
      "Summoned. Awaiting instructions.",
      "Present. State your orders."
    ],
    follow: ["Following.", "Understood. Staying close.", "Acknowledged."],
    stay: ["Holding position.", "Understood. I remain here.", "Position held."],
    come: ["Moving to you.", "En route.", "Coming."],
    guard: ["Guarding. Hostiles will be dealt with.", "Watch established.", "Perimeter is mine."],
    stop: ["Task cancelled.", "Stopped.", "Order rescinded. Standing by."],
    where: ["Position: {pos}.", "I am at {pos}.", "Location {pos}."],
    task_start: ["Executing: {task}.", "Beginning {task}.", "Task accepted: {task}."],
    task_done: ["Complete: {task}.", "{task} — finished.", "Task concluded."],
    unknown: ["Instruction unclear. '!ai help' lists valid forms.", "Cannot parse. Example: 'build a 5x5 floor'.", "Unrecognized order."],
    refuse: ["I answer to my owner alone.", "Denied. Owner only.", "Your authority is not recognized."],
    dismiss: ["Dismissed. Farewell.", "Withdrawing.", "Until next time."],
    okay: ["Done.", "Acknowledged.", "So it is."],
    help: ["Requesting assistance.", "Engaged and outmatched. Support needed.", "Backup required."],
    death: ["{name} has fallen. '!ai summon' will call another."]
  },
  heroic: {
    label: "Heroic",
    greet: [
      "Fear not, {owner} — your champion has arrived!",
      "The hour is met! What quest awaits us, {owner}?",
      "By blade and block, I stand ready!"
    ],
    follow: ["To your side, always!", "Where you walk, I follow — to glory!", "Onward together!"],
    stay: ["I shall hold this ground to the last!", "This spot is under my protection!", "None shall pass while I stand here!"],
    come: ["I ride to you!", "Hold fast — I am coming!", "To you, with haste!"],
    guard: ["Let the monsters come — they shall find me waiting!", "This watch is sacred. Rest easy!", "I guard this place with honour!"],
    stop: ["The quest is set aside — for now.", "Very well. The deed is halted.", "As you command; my blade rests."],
    where: ["I stand vigilant at {pos}!", "You will find your champion at {pos}!", "At {pos}, holding the line!"],
    task_start: ["A noble undertaking: {task}! It begins!", "For glory — {task}!", "The quest '{task}' is underway!"],
    task_done: ["Victory! {task} is complete!", "The deed is done: {task}!", "Another triumph: {task}!"],
    unknown: ["Alas, I know not this quest. Speak '!ai help' for the scrolls!", "The words escape me — try 'build a 5x5 floor', friend!", "A riddle beyond me! Phrase it as 'mine a 3x3 hole'!"],
    refuse: ["My oath binds me to my summoner alone!", "Only my liege commands this blade!", "Noble stranger, my loyalty is sworn elsewhere."],
    dismiss: ["I take my leave — call, and I shall return!", "My watch ends. Farewell, friend!", "Until the horn sounds again!"],
    okay: ["It is done!", "As you command!", "Consider it so!"],
    help: ["Even heroes need aid — to me, allies!", "I am beset! Join the fray!", "A worthy foe — perhaps too worthy! Help!"],
    death: ["{name} has fallen in battle! Honour them — then '!ai summon' another."]
  },
  shy: {
    label: "Shy",
    greet: [
      "Oh! Um. Hi {owner}... I'll do my best.",
      "H-hello... I'm here. Just tell me quietly what you need.",
      "Um. Reporting for duty... I guess?"
    ],
    follow: ["O-okay, following...", "I'll stay close... if that's alright.", "Right behind you... sorry if I lag."],
    stay: ["I'll just... wait here then.", "Okay... staying. Please come back soon.", "Um, holding this spot..."],
    come: ["C-coming...!", "On my way... don't watch me run, please.", "Okay! Coming over."],
    guard: ["I'll keep watch... I can be brave. Probably.", "Guarding... please don't be too far away.", "O-okay. Watching for monsters..."],
    stop: ["Oh — okay, stopping.", "S-sorry, cancelling that now.", "Stopped... was that right?"],
    where: ["Um... I'm at {pos}.", "I'm hiding— I mean, waiting at {pos}.", "At {pos}... please come get me."],
    task_start: ["I-I'll try: {task}...", "Okay... starting {task}. Wish me luck.", "Doing it now... ({task})"],
    task_done: ["I... I did it! {task} is done!", "Um. Finished: {task}. Was it okay?", "Done with {task}... phew."],
    unknown: ["S-sorry, I didn't understand... maybe '!ai help'?", "Um... could you say it like 'build a 5x5 floor'?", "I don't know that one... sorry..."],
    refuse: ["Um... I should really only listen to my owner. Sorry.", "S-sorry... owner's orders only.", "I... can't. You're not my owner."],
    dismiss: ["Oh... okay. Bye then... call me again sometime?", "G-goodbye... it was nice.", "Leaving now... thanks for having me."],
    okay: ["O-okay, done.", "Did it...!", "Um, all set."],
    help: ["H-help!! Please!!", "I'm scared and also losing — help!", "Someone?! Anyone?!"],
    death: ["{name} is... gone. Um. '!ai summon' brings a new friend."]
  }
};

export const PERSONALITY_IDS = Object.keys(PERSONALITIES);

export function personaLine(id, key) {
  const p = PERSONALITIES[id] || PERSONALITIES.friendly;
  const pool = p[key] || PERSONALITIES.friendly[key] || ["..."];
  return pick(pool);
}
