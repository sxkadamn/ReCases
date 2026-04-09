# ReCases Custom Item Examples

Готовые примеры для интеграции с `ItemsAdder`, `Oraxen`, `Nexo` и `MMOItems` лежат рядом с текстурами.

`ItemsAdder`
- Конфиг: `examples/custom-items/itemsadder/contents/recases_items/configs/recases_example_items.yml`
- Текстура: `examples/custom-items/itemsadder/contents/recases_items/textures/item/ruby_sword.png`
- Item id: `recases_items:ruby_sword`

`Oraxen`
- Конфиг: `examples/custom-items/oraxen/items/recases_example_items.yml`
- Текстура: `examples/custom-items/oraxen/pack/textures/recases_ruby_sword.png`
- Item id: `recases_ruby_sword`

`Nexo`
- Конфиг: `examples/custom-items/nexo/items/recases_example_items.yml`
- Текстура: `examples/custom-items/nexo/pack/assets/recases/textures/item/ruby_sword.png`
- Item id: `recases_ruby_sword`

`MMOItems`
- Конфиг предмета: `examples/custom-items/mmoitems/items/sword.yml`
- Override vanilla-модели: `examples/custom-items/mmoitems/resourcepack/assets/minecraft/models/item/diamond_sword.json`
- Модель предмета: `examples/custom-items/mmoitems/resourcepack/assets/recases/models/item/ruby_sword.json`
- Текстура: `examples/custom-items/mmoitems/resourcepack/assets/recases/textures/item/ruby_sword.png`
- Type / id: `SWORD / RECASES_RUBY_SWORD`
- CustomModelData: `10001`

Как использовать:
- Для `ItemsAdder` скопируй папку `contents/recases_items` в `plugins/ItemsAdder/contents/`.
- Для `Oraxen` скопируй `items/recases_example_items.yml` в `plugins/Oraxen/items/`, а PNG в `plugins/Oraxen/pack/textures/`.
- Для `Nexo` скопируй `items/recases_example_items.yml` в `plugins/Nexo/items/`, а папку `assets/recases` в `plugins/Nexo/pack/assets/recases/`.
- Для `MMOItems` добавь секцию `RECASES_RUBY_SWORD` в `plugins/MMOItems/item/sword.yml`, затем перенеси файлы из `resourcepack/assets/...` в свой серверный resource pack. Если у тебя уже есть `diamond_sword.json`, добавь в него override на `custom_model_data: 10001`, а не перезаписывай весь файл.
- После этого перезагрузи соответствующий плагин и используй id из примеров в `ReCases`.

Примеры в `recases-plugin/src/main/resources/config.yml` уже ссылаются именно на эти id.
