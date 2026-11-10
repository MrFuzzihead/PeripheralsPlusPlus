--[[
Created by Fxz_y

Usage: nearbyPlayers [range]
]]
local range = tonumber(({ ... })[1])

local p = peripheral.find("playerSensor")
if not p then
  print("No player sensor attached.")
  return
end

local data = range and p.getNearbyPlayers(range) or p.getNearbyPlayers()
for _, player in pairs(data) do
  for key, value in pairs(player) do
    print(tostring(key) .. ": " .. tostring(value))
  end
end
