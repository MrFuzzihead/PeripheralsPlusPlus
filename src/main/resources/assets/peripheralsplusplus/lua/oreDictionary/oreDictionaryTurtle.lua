--[[
Created by Fxz_y

Put an item in slot 1 and press T to transmute it into an equivalent ore-dictionary item.
]]
local p = peripheral.find("oreDictionary")
if not p then
  print("No ore dictionary attached.")
  return
end

while true do
  turtle.select(1)
  if turtle.getItemCount(1) == 0 then
    term.clear()
    term.setCursorPos(1, 1)
    print("Place an item in the first slot to transmute it.")
    os.pullEvent("turtle_inventory")
  else
    local entries = p.getEntries()
    term.clear()
    term.setCursorPos(1, 1)
    print("Press T to transmute!")
    for key, value in pairs(entries) do
      print(tostring(key) .. ": " .. tostring(value))
    end

    local waiting = true
    while waiting do
      local event, char = os.pullEvent()
      if event == "char" and char == "t" then
        p.transmute()
        waiting = false
      elseif event == "turtle_inventory" then
        waiting = false
      end
    end
  end
end
