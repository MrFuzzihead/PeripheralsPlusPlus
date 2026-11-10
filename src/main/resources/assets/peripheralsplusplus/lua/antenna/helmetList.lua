--[[
Created by Fxz_y
]]
local p = peripheral.find("antenna")
if not p then
  print("No antenna attached.")
  return
end
for _, name in pairs(p.getPlayers()) do
  print("-" .. tostring(name))
end
