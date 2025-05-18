🧠 Inspiration
As students who run a robotics club, we’ve seen the same thing happen over and over again:
New members join, excited to learn electronics — but the moment breadboards and schematics come out, they freeze.
The diagrams are confusing.
The connections aren’t clear.
One small mistake can break everything.
Eventually, they give up — not because they lack potential, but because the tools fail them.
We built GuideLine because everyone deserves a chance to build and learn without being held back by bad UX and intimidating tools.

💡 What It Does
GuideLine is an augmented reality tool that visually guides users through the breadboarding process.
Upload a schematic (from Fritzing, KiCad, or EAGLE)
Point your phone at your breadboard
See glowing highlights showing exactly where each wire and component goes
Follow step-by-step instructions until your circuit is complete
It’s like a GPS for electronics.


⚙️ How It Works
AR & Computer Vision detect and calibrate the physical breadboard layout
Schematic Parser extracts wiring instructions and component locations
Real-Time AR Overlay lights up individual holes for each connection
Step-by-Step Mode guides the user through each placement with optional haptic feedback![](https://cdn.discordapp.com/attachments/1369872014587859065/1373378221113868388/Screenshot_2025-05-17_151428.png?ex=682a31a4&is=6828e024&hm=4e48ab8fe4e252db0a2c393e2fd5b73390864b3778fd917a6e24e28fcc30b301&=)



🏆 Accomplishments
Functional AR interface with real-time wiring overlays
Accurate schematic parsing from Fritzing exports
Breadboard recognition using corner detection + homography mapping
Optimized mobile performance to maintain 30 FPS with AR overlays

📚 What We Learned
Computer vision + AR is way more fragile on mobile than we expected
Robust schematic parsing across platforms (Fritzing, KiCad, EAGLE) is non-trivial
UI/UX matters as much as the tech — if users don’t trust it, they won’t use it
Cross-functional collaboration is essential in CV, AR, and mobile development

🚀 What’s Next for GuideLine
Add support for more schematic formats (e.g. KiCad netlists, JSON)
Build a real-time feedback system to detect incorrect placements
Integrate with education platforms (classroom/curriculum support)
Launch a community schematic library for beginners
Polish the UI, expand to iOS, and test in schools & makerspaces
