# Интерфейс `lcmf-registry`

Этот документ описывает публичный интерфейс библиотеки
[`lcmf-registry`](https://github.com/algebrain/lcmf-registry).

Библиотека реализует read-provider registry для
[`LCMF`](https://github.com/algebrain/lcmf-docs).

## Назначение

`lcmf-registry` дает:

- регистрацию публичных read providers;
- декларацию обязательных внешних read-зависимостей;
- проверку корректности wiring;
- fail-fast проверку на старте приложения;
- sealing после успешной стартовой проверки.

Библиотека не:

- заменяет `bus`;
- координирует side effects;
- предназначена для каскадных provider -> provider вызовов.

## Локальная проверка

Основной локальный вход в контур проверки:

```bash
bb test.bb
```

Отдельные команды:

- `clojure -M:test` — `cljs/node` тесты через `shadow-cljs`
- `clojure -M:test-watch` — watch-режим для локальной разработки
- `clojure -M:lint` — `clj-kondo`
- `clojure -M:format` — `cljfmt`

## `make-registry`

Создает in-memory registry.

Общая форма:

```clojure
(make-registry)
```

Пример:

```clojure
(ns my.app
  (:require [lcmf.registry :as registry]))

(def app-registry
  (registry/make-registry))
```

Начальное состояние registry:

```clojure
{:providers {}
 :requirements {}
 :sealed? false}
```

## `register-provider!`

Регистрирует публичный provider.

Общая форма:

```clojure
(register-provider! registry
  {:provider-id ...
   :module ...
   :provider-fn ...
   :meta ...})
```

Обязательные поля:

- `:provider-id` — keyword
- `:module` — keyword
- `:provider-fn` — function

Optional:

- `:meta` — map

Важно:

- лишние ключи в provider spec не допускаются;
- после успешного `assert-requirements!` registry запечатывается, и новые
  provider-ы регистрировать уже нельзя.

Пример:

```clojure
(registry/register-provider!
 app-registry
 {:provider-id :accounts/get-by-id
  :module :accounts
  :provider-fn (fn [{:keys [user-id]}]
                 (when-let [user (get-in @accounts-state [:users user-id])]
                   {:id (:id user)
                    :login (:login user)
                    :role (:role user)}))
  :meta {:version "1.0"}})
```

Поведение:

- при duplicate `provider-id` бросает `ex-info` с `:reason :duplicate-provider`
- при невалидных аргументах бросает `ex-info` с `:reason :invalid-argument`
- после sealing бросает `ex-info` с `:reason :registry-sealed`

## `resolve-provider`

Возвращает provider function или `nil`, если provider отсутствует.

Общая форма:

```clojure
(resolve-provider registry provider-id)
```

Пример:

```clojure
(if-let [get-user (registry/resolve-provider app-registry :accounts/get-by-id)]
  (get-user {:user-id "u-alice"})
  nil)
```

## `require-provider`

Возвращает provider function или бросает исключение, если provider отсутствует.

Общая форма:

```clojure
(require-provider registry provider-id)
```

Пример:

```clojure
(let [get-user (registry/require-provider app-registry :accounts/get-by-id)]
  (get-user {:user-id "u-alice"}))
```

Поведение:

- при отсутствии provider бросает `ex-info` с `:reason :missing-provider`

## `declare-requirements!`

Объявляет обязательные provider-зависимости модуля.

Общая форма:

```clojure
(declare-requirements! registry module required-provider-ids)
```

Где:

- `module` — keyword
- `required-provider-ids` — set of keywords

Пример:

```clojure
(registry/declare-requirements!
 app-registry
 :booking
 #{:accounts/get-by-id
   :catalog/get-slot-by-id})
```

Поведение:

- repeated declarations для того же модуля merge-ятся
- после sealing бросает `ex-info` с `:reason :registry-sealed`

## `validate-requirements`

Проверяет, закрыты ли все declared requirements зарегистрированными providers.

Общая форма:

```clojure
(validate-requirements registry)
```

Возвращает:

```clojure
{:ok? true
 :missing {}
 :registered-provider-ids #{:accounts/get-by-id}
 :declared-requirements {:booking #{:accounts/get-by-id}}}
```

или

```clojure
{:ok? false
 :missing {:booking #{:accounts/get-by-id}}
 :registered-provider-ids #{}
 :declared-requirements {:booking #{:accounts/get-by-id}}}
```

Пример:

```clojure
(registry/validate-requirements app-registry)
```

## `assert-requirements!`

Выполняет fail-fast проверку на старте приложения.

Общая форма:

```clojure
(assert-requirements! registry)
```

Пример:

```clojure
(registry/assert-requirements! app-registry)
```

Поведение:

- при незакрытых зависимостях бросает `ex-info` с
  `:reason :missing-required-providers`
- при успешной проверке запечатывает registry, выставляя `:sealed? true`

## Минимальный walkthrough

```clojure
(ns my.app
  (:require [lcmf.registry :as registry]))

(def app-registry
  (registry/make-registry))

;; accounts
(registry/register-provider!
 app-registry
 {:provider-id :accounts/get-by-id
  :module :accounts
  :provider-fn (fn [{:keys [user-id]}]
                 (when-let [user (get-in @accounts-state [:users user-id])]
                   {:id (:id user)
                    :login (:login user)
                    :role (:role user)}))})

;; booking
(registry/declare-requirements!
 app-registry
 :booking
 #{:accounts/get-by-id})

;; app startup
(registry/assert-requirements! app-registry)

;; consumer module code
(let [get-user (registry/require-provider app-registry :accounts/get-by-id)]
  (get-user {:user-id "u-alice"}))
```
