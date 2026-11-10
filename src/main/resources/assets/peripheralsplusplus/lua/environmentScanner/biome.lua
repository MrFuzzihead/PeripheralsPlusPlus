--[[
Created by Fxz_y
]]
local p = peripheral.find("environmentScanner")
if not p then
  print("No environment scanner attached.")
  return
end
print("You are in a " .. tostring(p.getBiome()) .. " biome.")
