# Limit Keeper

A [RuneLite](https://runelite.net) plugin that tracks how much of each item's four hour Grand
Exchange buy limit you have already used.

RuneLite already tells you what an item's buy limit is, and when the period resets. What it does
not tell you is the number that actually matters: how many you have bought so far, and therefore
how many more you can still buy. Limit Keeper keeps that count.

## What it does

- **On the buy offer screen** it adds a line under the item text:

  ```
  Bought: 8,240 / 11,000  2,760 left  resets in 2:13:04
  1.2M gp spent (146 ea)
  ```

- **In a side panel** it lists every item with a period running, soonest to reset first, with a
  progress bar and a live countdown.
- **Optional notifications** when you use up an item's limit, and when a limit you had used up
  becomes available again.

Counts are kept per account, so alts are tracked separately, and they survive a client restart.

## Spend and average price

Alongside the quantity, each period totals the coins spent and divides it out to an average paid
per item. The market price on the offer screen is what the item is going for; this is what *you*
actually paid for the ones you have bought so far this period, which is the number worth watching
when you are buying into a rising item or filling an offer over several hours.

## How it works

Buy limits themselves come from RuneLite's own item data, so there is no bundled item table to go
stale and the plugin makes no network requests of its own.

Purchases are counted from Grand Exchange offer updates. A snapshot of each of the eight slots is
saved - item, price, total, quantity filled and coins spent - so amounts bought during an earlier
session are counted once rather than being replayed as new purchases at the next login.

The four hour period starts at the first purchase of an item, matching the game, and the count is
cleared once it elapses.

## Known limits

- Buys made outside this client - on mobile, or in another client - are not seen, so counts can
  read low. Same for an offer that is placed, filled and collected entirely while you are logged
  out of RuneLite.
- Items RuneLite has no buy limit for are shown with a plain count instead of a limit. They can be
  hidden from the panel in the settings.
- If a count ever drifts, **Clear** in the side panel forgets everything tracked for that account.

## Settings

| Setting | Default | Description |
| --- | --- | --- |
| Show on offer screen | on | Adds the remaining limit to the Grand Exchange buy offer screen. |
| Side panel | on | Shows the side panel of running periods. |
| Hide unknown limits | off | Hides items with no known buy limit from the panel. |
| Limit reached | off | Notifies you when an item's limit is used up. |
| Limit reset | off | Notifies you when a limit you had used up is available again. |

## Building

```
./gradlew run
```

This launches a development RuneLite client with the plugin loaded. To sign in with a Jagex
account, follow [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).
