--[[
Created by Fxz_y

Shows the barrel turtle's contents. Press P to put the selected item in, G to take a stack out.
]]
local p = peripheral.find("barrel")
if not p then
  print("No barrel attached.")
  return
end

local function draw(header)
  term.clear()
  term.setCursorPos(1, 1)
  print(header)
end

while true do
  local name = p.getLocalizedName()
  local amount = p.getAmount()

  if not name then
    draw("Nothing in the barrel.")
    print("Press P to put the selected item in.")
    local _, char = os.pullEvent("char")
    if char == "p" then
      for slot = 1, 16 do
        turtle.select(slot)
        local item = turtle.getItemDetail()
        if item then
          p.put()
        end
      end
      turtle.select(1)
    end
  else
    draw("Item in barrel: " .. tostring(name) .. " (" .. tostring(amount) .. ")")
    print("Press P to put the selected item in.")
    print("Press G to take a stack out.")
    local _, char = os.pullEvent("char")
    if char == "p" then
      for slot = 1, 16 do
        turtle.select(slot)
        local item = turtle.getItemDetail()
        if item and item.name == p.getUnlocalizedName() then
          p.put()
        end
      end
      turtle.select(1)
    elseif char == "g" then
      for slot = 1, 16 do
        turtle.select(slot)
        if p.get() then
          break
        end
      end
      turtle.select(1)
    end
  end
end
