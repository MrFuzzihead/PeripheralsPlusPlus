--[[
Created by Fxz_y
]]
local p = peripheral.find("environmentScanner")
if not p then
  print("No environment scanner attached.")
  return
end

local snow = p.isSnow()
local rain = p.isRaining()
if snow then
  print(rain and "It is snowing." or "It is not snowing.")
else
  print(rain and "It is raining." or "It is not raining.")
end
