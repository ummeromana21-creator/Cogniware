# Fly Experimenter

An experimenter agent for the virtual fruit-fly neuroscience lab, built in the
shape of the legal-agent workshop: a small agentic loop over a tool kit, with
runs saved on disk. The agent drives the lab over its WebSocket protocol —
observing Maxime (female) and Hagrid (male) in their control and treatment
boxes, dosing the treatment box, running trials and experiment cards, and
filing a report with numbers that came only from tool results.

No case-specific logic lives in the code: the model chooses every tool call,
and every run is reproducible from `workspace/`.

## Commands

```bash
cp .env.example .env   # add MODEL_API_KEY
./helper setup         # create .venv, install requirements
./helper run "does caffeine 0.5 make the flies walk more?" [--turns 30]
./helper run --resume RUN_ID [--turns 15]
./helper test          # offline suite: fake lab + fake model, no server, no key
./helper review RUN_ID # print what a run did
```
