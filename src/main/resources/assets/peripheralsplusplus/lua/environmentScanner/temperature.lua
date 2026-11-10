--[[
Created by Fxz_y
]]
local p = peripheral.find("environmentScanner")
if not p then
  print("No environment scanner attached.")
  return
end
local temp = p.getTemperature()
print("This biome has a " .. string.lower(tostring(temp)) .. " temperature.")
