# Pull request

## What changed


## Why


## Live test

<!-- Delete what doesn't apply. "Code only" is a valid answer. It is not a valid answer when you saw it work. -->

- Where: <!-- singleplayer / anarchy server / not tested -->
- What I did:
- What I observed:
- **Not observed:** <!-- anything you're inferring, and why you couldn't check it -->

## Risk

- [ ] Touches the tick / a driver / the connection
- [ ] Touches the panel
- [ ] Changes an existing behaviour
- [ ] New dependency (say why below)

## Checklist

- [ ] `gradlew build` is green
- [ ] No version bump
- [ ] No secrets, no `run/`, no `logs/`, no built jars committed
- [ ] Tested on a **copied** world, singleplayer, alone, no `/tp`, no second player
- [ ] Red lines respected (anarchy only, ender chest only, proxy modules untouched)
- [ ] If it touches the panel: token on every request, `Origin` check intact, no `innerHTML` with server text
