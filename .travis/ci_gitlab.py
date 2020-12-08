
import os
import subprocess

branch = os.environ['CI_COMMIT_BRANCH']
commit_message = os.environ['CI_COMMIT_MESSAGE']

release_properties_exists = os.path.exists('release.properties')

print('travis ci.py')
print('')
print('')
#print('Repo: %s' % os.environ['TRAVIS_REPO_SLUG'])
print('Branch: %s' % branch)
print('Release?: %s' % str(release_properties_exists))
print('Commit: %s' % commit_message)

def package_conda():
    subprocess.check_call(['sh', 'populate_fiji.sh'])
    subprocess.check_call(['pyinstaller', '--onefile', '--add-data', 'Fiji.app/jars:jars', 'src/main/python/sciview.py'])

    platform = subprocess.check_output(['uname', '-s']).decode('UTF-8')
    arch = subprocess.check_output(['uname', '-m']).decode('UTF-8')

    print(['platform, arch', platform, arch])
    
    if 'Linux' in platform and 'x86_64' in arch:
        exe_name = 'sciview-linux64'
    elif 'Linux' in platform:
        exe_name = 'sciview-linux32'
    elif 'Darwin' in platform:
        exe_name = 'sciview-macos'
    elif 'MING' in platform:
        exe_name = 'sciview-win32'
    elif 'MSYS_NT' in platform:
        exe_name = 'sciview-win32'

    print(['exe:', exe_name])

    subprocess.check_call(['mv', 'dist/sciview', exe_name])

package_conda()
