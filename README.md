# HoneypotAPI-Test-Plugin

A test plugin to demonstrate the features of the Honeypot API. [Check out Honeypot here!](https://github.com/TerrrorByte/Honeypot)

To use, run `/testcommand` in game to toggle cancelling player break and interact events.
You can also run `/checkblock` to see if it's a Honeypot or not.

If you want to demo Behavior Providers, create a Honeypot with the type of 'chicken-storm'. Warning -- turn down your volume ;)

## Configurable Options (Honeypot 4+)
- `chicken-count`: Number of chickens used to build the tornado. Higher values create a wider and taller funnel. Default: 50
- `die-after`: Time in seconds the tornado persists after it finishes growing. Default: 10
- `throw-blocks`: When `true`, the roaming tornado can rip up nearby solid blocks and fling them as falling-block debris. Default: `false`
