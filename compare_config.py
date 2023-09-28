import json
import os

NEW_CONFIG_FOLDER="config-dir"
CURRENT_CONFIG_FOLDER="src/main/resources/META-INF/native-image"

PREFIX_FILTER = ["org.apache.maven", "org.sonatype.plexus", "org.eclipse.sisu", "org.eclipse.aether", "org.codehaus.plexus", "org.fusesource.jansi"]

def compare(file_name, new_config_folder, current_config_folder):

    with open(to_absolute_path(current_config_folder + "/" + file_name)) as current_config_file:
        current_config = json.load(current_config_file)

    with open(to_absolute_path(new_config_folder + "/" + file_name)) as new_config_file:
        new_config = json.load(new_config_file)

    print(file_name)
    print("="*10)
    for entry in new_config:
        if filter_out(entry):
            continue

        if entry not in current_config:
            print(json.dumps(entry))
    print("\n")


def to_absolute_path(relative_path):
    absolute_path = os.path.dirname(__file__)
    return os.path.join(absolute_path, relative_path)


def filter_out(reflection_entry):
    if "name" not in reflection_entry:
        return False
    
    for prefix in PREFIX_FILTER:
        if reflection_entry["name"].startswith(prefix):
            return True

    return False


for file in ("jni-config.json", "proxy-config.json", "reflect-config.json"):
    compare(file, NEW_CONFIG_FOLDER, CURRENT_CONFIG_FOLDER)