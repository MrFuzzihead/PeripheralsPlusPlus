--[[
Created by Fxz_y
]]
local p = peripheral.find("peripheralContainer")
if not p then
  print("No peripheral container attached.")
  return
end

local list = p.getContainedPeripherals()
print("The container holds " .. #list .. " peripheral(s):")
for _, name in pairs(list) do
  print("-" .. tostring(name))
end
