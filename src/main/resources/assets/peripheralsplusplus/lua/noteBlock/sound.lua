--[[
Created by Fxz_y

Usage: sound <sound> <volume> <pitch>
Vanilla sound names look like "random.wood_click" or "mob.zombie.say".
]]
local p = peripheral.find("noteBlock")
if not p then
  print("No note block attached.")
  return
end

local tArgs = { ... }
local sound = tArgs[1]
local volume = tonumber(tArgs[2])
local pitch = tonumber(tArgs[3])
if not sound or not volume or not pitch then
  print("Usage: sound <sound> <volume> <pitch>")
  return
end
p.playSound(sound, volume, pitch)
