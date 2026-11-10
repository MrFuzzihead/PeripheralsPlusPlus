--[[
Created by Fxz_y
]]
local p = peripheral.find("playerSensor")
if not p then
  print("No player sensor attached.")
  return
end
for key, name in pairs(p.getAllPlayers()) do
  print(tostring(key) .. ": " .. tostring(name))
end
