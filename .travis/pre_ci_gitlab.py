
import os
import subprocess

branch = os.environ['CI_COMMIT_BRANCH']
commit_message = os.environ['CI_COMMIT_MESSAGE']

release_properties_exists = os.path.exists('release.properties')

print('gitlab-ci pre_ci.py')
print('')
print('')
#print('Repo: %s' % os.environ['TRAVIS_REPO_SLUG'])
print('Branch: %s' % branch)
print('Release?: %s' % str(release_properties_exists))
print('Commit: %s' % commit_message)

# Perform main build
print('Starting build')
key_name = 'encrypted_eb7aa63bf7ac_key'
key_iv = 'encrypted_eb7aa63bf7ac_iv'

subprocess.check_call(['bash', 'travis-build-gitlab.sh'])

# Setup conda environment
# def build_conda():
#     from pathlib import Path
#     home = str(Path.home())

#     print('------ BUILD CONDA -----')
    
#     script_name = 'Miniconda3-latest-Linux-x86_64.sh'
#     miniconda_dir = '%s/miniconda' % home
#     subprocess.call(['curl', '-fsLO', 'https://repo.continuum.io/miniconda/%s' % script_name])
#     subprocess.call(['bash', script_name, '-b', '-p', miniconda_dir])
#     subprocess.call(['source', '%s/etc/profile.d/conda.sh' % miniconda_dir])
#     subprocess.call(['hash', '-r'])
#     subprocess.call(['conda', 'config', '--set', 'always_yes', 'yes', '--set', 'changeps1', 'no'])
#     subprocess.call(['conda', 'update', '-q', 'conda'])
#     # Useful for debugging any issues with conda
#     subprocess.call(['conda', 'info', '-a'])

#     # Replace dep1 dep2 ... with your dependencies
#     subprocess.call(['conda', 'env', 'create', '-f', 'environment.yml'])
#     subprocess.call(['conda', 'activate', 'sciview'])
# build_conda()
