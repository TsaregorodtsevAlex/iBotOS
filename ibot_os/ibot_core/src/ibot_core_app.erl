-module(ibot_core_app).

-include("debug.hrl").
-include("..\\..\\ibot_db/include/ibot_db_table_names.hrl").
-include("..\\..\\ibot_db/include/ibot_db_project_config_param.hrl").
-include("ibot_core_create_project_paths.hrl").
-include("ibot_core_modules_names.hrl").

-behaviour(application).

%% Application callbacks
-export([start/2, stop/1]).
-export([create_project/2, create_node/2, connect_to_project/1, get_project_nodes/0, get_project_node_from_config/0]).
-export([get_cur_dir/0]).
-export([add_node_name_to_config/1]).
-export([start_project/0, start_node/1]).

%% ===================================================================
%% Application callbacks
%% ===================================================================

start(_StartType, _StartArgs) ->
    ibot_core_sup:start_link().

stop(_State) ->
    ibot_db_func:delete_table(?TABLE_CONFIG), %% Удаляем таблицу с данными конфируции проекта
    ok.


%% @doc Create project
%% @spec create_project(Dir, Path) -> ok | {error, Reason} when Dir :: string(), Path :: string().
%% @end
-spec create_project(Dir, Path) -> ok | {error, Reason}
  when Dir :: string(), Path :: string(), Reason :: term().

create_project(Path, Dir) ->
  ibot_db_func:add(?TABLE_CONFIG,
    ?FULL_PROJECT_PATH, string:join([Path, Dir], ?PATH_DELIMETER_SYMBOL)), %% Add project full path to config
  ibot_core_func_cmd_cdir:create_project(Path, Dir). %% Create project directories


%% @doc
%% Создание узла / Create node
%% @spec create_node(NodeName, NodeLang) -> ok when NodeName :: string(), NodeName :: string().
%% @end
-spec create_node(NodeName, NodeLang) -> ok when NodeName :: string(), NodeLang :: string().

create_node(NodeName, NodeLang) ->
  ?DMI("create_node", {NodeName, NodeLang}),
  ibot_core_func_cmd_cdir:create_node(NodeName, list_to_atom(NodeLang)),
  ok.


%% @doc
%% Подключение к проекту / Connect to project
%% @spec connect_to_project(ProjectPath) -> error | ok when ProjectPath :: string().
%% @end
-spec connect_to_project(ProjectPath) -> error | ok when ProjectPath :: string().

connect_to_project(ProjectPath) ->
  ibot_db_func_config:set_full_project_path(ProjectPath),
  case ibot_core_app:get_project_nodes() of
    {error} -> error;
    {ok, ProjectNodes} ->
      parse_nodes_config_file(ProjectNodes),
      ok
  end.


%%% ====== parse_nodes_config_file mathod start ======

%% @doc
%% Parse node config file
%% @end

parse_nodes_config_file([NodeItem | NodesList]) ->
  case ?PATH_TO_NODE(NodeItem, ibot_db_srv_func_project:get_projectStatus()) of
    ?ACTION_ERROR -> ?ACTION_ERROR; %% ошбика действия
    ?FULL_PROJECT_PATH_NOT_FOUND -> ?FULL_PROJECT_PATH_NOT_FOUND; %% путь до проекта не найден
    NodePath ->
      %% получили полный путь до узла
      case filelib:is_file(NodePath) of
        %% парсим конфиг файл узла
        true -> gen_server:call(?IBOT_CORE_SRV_PROJECT_INFO_LOADER, {?LOAD_PROJECT_NODE_INFO, NodePath});
        %% узел по данному пути не обнарежен
        false -> ?DMI("parse_nodes_config_file node NOT FOUND", [NodePath])
      end
  end,
  %% парсим конфиг следующего узла
  parse_nodes_config_file(NodesList),
  ok;
%% все конфиг файлы узлов загружены
parse_nodes_config_file([]) ->
  ok.

%%% ====== parse_nodes_config_file mathod end ======




%%% ====== get_project_nodes mathod start ======

%% @doc
%% Список узлов проектов / Get project nodes list
%% @end

get_project_nodes() ->
  {ok, ibot_db_func_config:get_nodes_name_from_config()}.


%% @doc
%% Список и информация об узлах проекта в виде строки / List and information about project nodes as string
%% @end

get_project_node_from_config() ->
  ?DMI("get_project_node_from_config", [ibot_db_func_config:get_all_registered_nodes()]),
  {ok, ibot_db_func_config:get_all_registered_nodes()}.

%%% ====== get_project_nodes mathod end ======

add_node_name_to_config([NodeName| NodeNamesList])->
  ibot_db_func_config:add_node_name_to_config(NodeName),
  add_node_name_to_config(NodeNamesList),
  ok;
add_node_name_to_config([]) ->
  ?DMI("add_node_name_to_config", [ibot_db_func_config:get_nodes_name_from_config()]),
  ok.


%% @doc
%% Текущая директория ядра / Current core directory
%% @end

get_cur_dir() ->
  ?DMI("get_cur_dir", [file:get_cwd()]),
  file:get_cwd().


%% @doc
%% Запуск проекта / Start project
%% @end

start_project() ->
  Nodes = ibot_db_func_config:get_nodes_name_from_config(),
  ?DMI("start_project", [Nodes]),
  run_project_node(Nodes),
  ok.


%% @doc
%% Запуск узла / Node start
%% @end

start_node(NodeName) ->
  run_project_node([NodeName]),
  ok.


%% @doc
%% Запуск списка узлов / Start nodes list
%% @end

run_project_node([NodeName | NodeNamesList]) ->
  gen_server:cast(ibot_services_srv_connector, {start_node, ibot_db_func_config:get_node_info(list_to_atom(NodeName))}),
  run_project_node(NodeNamesList);
run_project_node([]) ->
  ok.

