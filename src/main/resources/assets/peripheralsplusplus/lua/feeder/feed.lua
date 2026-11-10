--[[
Created by Fxz_y

Usage: feed [amount]
Feeds the animals around the turtle one item at a time.
]]
local amount = tonumber(({ ... })[1]) or 1

local p = peripheral.find("feeder")
if not p then
  print("No feeder attached.")
  return
end

for _ = 1, amount do
  p.feed()
end
