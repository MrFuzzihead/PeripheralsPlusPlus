--[[
Created by austinv11
]]
local p = peripheral.find("compass")
if not p then
  print("No compass attached.")
  return
end
print("The turtle is currently facing: " .. tostring(p.getFacing()))
