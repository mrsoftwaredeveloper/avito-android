# Сборки в контейнерах

--8<--
avito-disclaimer.md
--8<--

[Avito Docker documentation (internal)](http://links.k.avito.ru/cfxOMToAQ)

Все образы расположены в `ci/docker`.

## Android SDK image

This is the base image with Android Build Tools.  
It's not ready yet, see MBS-7071.

## Android builder image

This is the image for building and testing Android applications. It contains Android SDK.

### How to update android-builder image?

1. Build the image to test your changes

=== "In CI"

    Run [Build android-builder (internal)](http://links.k.avito.ru/tmctAvitoAndroidBuilder) teamcity configuration.  
    You will see the tag in stdout:
    
    ```text
    Image *******/android/builder:eb4a3b67e564 has been published successfully
    ```

=== "Locally"

    ```bash
    export DOCKER_REGISTRY=<docker registry>
    cd ci/docker
    ./publish.sh <directory with Dockerfile>
    ```
    
    This script will build a new image. You will the tag in stdout:
    
    ```text
    Successfully built eb4a3b67e564
    ```
    
    To push the image you must have registry credentials in these envs: `DOCKER_LOGIN`, `DOCKER_PASSWORD` .  
    Without it the script will stop after building.

1. [Upload the image to Docker Hub](#uploading-image-to-docker-hub)
1. Update image hash in `IMAGE_ANDROID_BUILDER` variable in ci shell scripts:
    - In github repo: `ci/_environment.sh` 
    - In internal avito repository: `ci/_main.sh`
1. Check this images is working. At least, run `ci/local_check.sh`.
1. Make PR with a new image.

## Docker in docker image

Утилитарный образ с докером внутри.  
Используем внутри скриптов для создания и публикации других образов, прежде всего эмулятора.

### How to update itself?

Образ собирает сам себя с помощью предыдущей версии образа (bootstrapping):  
`./publish.sh docker-in-docker-image`  
`publish.sh` - использует текущую версию образа  
`docker-in-docker-image` - содержит изменения

Если меняем контракт с окружением, то вносим правки поэтапно, чтобы прошлая версия образа могла собрать новую.

[Build docker-in-docker (internal)](http://links.k.avito.ru/tmctAvitoAndroidDockerInDocker)

## Android emulator images

Эмуляторы имеют кастомные настройки, оптимизированы для стабильности и производительности.

- Небольшое разрешение экрана: 320x480, 4 inch
- Отключены многие фичи

### Как запустить эмулятор?

=== "macOS/Windows"

    CI эмулятор невозможно запустить из-за ограничений виртуализации [haxm #51](https://github.com/intel/haxm/issues/51#issuecomment-389731675).
    Поэтому воспроизводим идентичную конфигурацию.
    
    - Создай эмулятор в Android Studio: WVGA (Nexus One) с размером экрана 3.4'' и разрешением 480x800.
    - Запусти эмулятор
    - Настрой параметры:
    
    ```bash
    adb root
    adb shell "settings put global window_animation_scale 0.0"
    adb shell "settings put global transition_animation_scale 0.0"
    adb shell "settings put global animator_duration_scale 0.0"
    adb shell "settings put secure spell_checker_enabled 0"
    adb shell "settings put secure show_ime_with_hard_keyboard 1"
    adb shell "settings put system screen_off_timeout 1800000"
    adb shell "settings put secure long_press_timeout 1500"
    ```
    
    - Перезагрузи эмулятор
    
    См. все настройки в `android-emulator/hardware` и `android-emulator/prepare_snapshot.sh`
    
    [Задача на автоматизацию (internal)](http://links.k.avito.ru/MBS7122)

=== "Linux"

    Проще и надежнее использовать оригинальные CI эмуляторы.
    
    Требования:
    
    - Docker
    - [KVM](https://developer.android.com/studio/run/emulator-acceleration#vm-linux)
    
    
    - Найди актуальную версию образа в `Emulator.kt`.
    - Разреши подключение к Xorg серверу с любого хоста (изнутри контейнера в нашем случае):
    
    ```bash
    xhost +
    ```
    
    - Запусти эмулятор:
    
    ```bash
    docker run -d \
        -p 5555:5555 \
        -p 5554:5554 \
        -e "SNAPSHOT_ENABLED"="false" -e "WINDOW"="true" --volume="/tmp/.X11-unix:/tmp/.X11-unix:rw" \
        --privileged \
        <registry>/android/emulator-27:<TAG>
    ```
    
    Или в headless режиме:
    
    ```bash
    docker run -d \
        -p 5555:5555 \
        -p 5554:5554 \
        --privileged \
        <registry>/android/emulator-27:<TAG>
    ```
    
    - Подключись к эмулятору в adb
    
    ```bash
    adb connect localhost:5555
    ```

### Как обновить образ?

Для эмулятора нужна более сложная подготовка, поэтому используем отдельные скрипты и образы.

#### 1. Залей образы в приватный Docker registry

=== "CI"

    1. Собери образ на ветке в Teamcity конфигурации [Build android-emulator (internal)](http://links.k.avito.ru/Y3).  
    Теги новых образов будут в артефактах сборки.
    1. Обнови теги в build.gradle скриптах.
    1. Запушь изменение в ветку.

=== "Local"

    Требования:
    
    - Linux, docker
    - [KVM](https://developer.android.com/studio/run/emulator-acceleration#vm-linux)
    - K8S права на push образов в registry-mobile-apps (env переменные DOCKER_LOGIN, DOCKER_PASSWORD)
    
    1. Запусти скрипт:
    
    ```bash
    cd ci/docker
    ./publish_emulator android-emulator
    ``` 
    
    Соберет образ, протестирует и запушит в docker registry.
    
    1. Найти новые теги образов.
    См. stdout скрипта или файл `android-emulator/images.txt`
    1. Обнови теги образов в build.gradle скриптах.

#### 2. Залей образы в Docker hub

[Uploading image to Docker Hub](#uploading-image-to-docker-hub)

### Как проверить регрессию?

- Прогони instrumentation dynamic чтобы выявить возможную утечку памяти.  
Для этого запусти компонентный тест с большим числом повторов.
- Прогони fullCheck  
Сравни количество тестов по всем статусам, не стало ли больше упавших или потерянных.

### Как проверить сколько ресурсов тратит эмулятор?

Локально используем [cAdvisor](https://github.com/google/cadvisor)

```bash
sudo docker run \
  --volume=/:/rootfs:ro \
  --volume=/var/run:/var/run:ro \
  --volume=/sys:/sys:ro \
  --volume=/var/lib/docker/:/var/lib/docker:ro \
  --volume=/dev/disk/:/dev/disk:ro \
  --publish=8080:8080 \
  --detach=true \
  --name=cadvisor \
  google/cadvisor:latest
```

В CI смотрим в метрики куба.

## Docker Hub

Образы публикуем в [hub.docker.com/u/avitotech](https://hub.docker.com/u/avitotech).

### Uploading image to Docker Hub

Пока что заливаем вручную, задача на автоматизацию: MBS-8773.

1. Залогинься в Docker hub

```bash
docker login --username=avitotech --password=...
```

2. Скачай новый образ из приватного registry

```bash
docker pull <DOCKER_REGISTRY>/android/<image>:<DIGEST>
```

Пример: 

```bash
docker pull registry/android/android-emulator-29:c0de63a4cd
```

3. Поставь образу tag равный digest из приватного registry

```bash
docker tag <DIGEST> avitotech/android-emulator-<API>:<DIGEST>
```

Пример: 

```bash
docker tag c0de63a4cd avitotech/android-emulator-29:c0de63a4cd`
```

Tag нужен чтобы ссылаться на образ по одним и тем-же координатам. 
Digest в разных registry может не совпадать 
([images ID does not match registry manifest digest](https://github.com/docker/distribution/issues/1662#issuecomment-213079540)).

4. Залей образ

```bash
docker push avitotech/android-emulator-<API>:<DIGEST>`
```

Пример: 

```bash
docker push avitotech/android-emulator-29:c0de63a4cd
```

## Best practices

### Reproducible image

Хотим получать одинаковый образ на любой машине, в любом окружении. 
Это упрощает отладку проблем и делает сборку более надежной. 

[reproducible-builds.org](https://reproducible-builds.org/docs/definition/)

Источники нестабильности:

- Не указана явно версия зависимости.
- Копируем в образ файлы, сгенерированные вне докера.   
Глядя на такие файлы трудно сказать в каком окружении они созданы, какое содержание ожидаемое.
