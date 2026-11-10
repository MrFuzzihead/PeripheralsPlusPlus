--[[
Created by Fxz_y
]]
local p = peripheral.find("playerSensor")
if not p then
  print("No player sensor attached.")
  return
end

local _, name = os.pullEvent("player")
print(tostring(name) .. " just right-clicked the sensor.")
